/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl.r

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.{SqlUtils, Template}
import cz.vse.easyminer.miner.MinerResultHeader.MiningTime
import cz.vse.easyminer.miner.impl.BoolExpressionImpl._
import cz.vse.easyminer.miner.impl.{ItemText, MysqlMinerDatasetOps, MysqlQueryBuilder}
import cz.vse.easyminer.miner.{Attribute, _}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlAttributeOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.InstanceTable
import org.slf4j.LoggerFactory
import scalikejdbc.interpolation.SQLSyntax

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

/**
  * Created by Vaclav Zeman on 20. 11. 2015.
  */
trait AprioriMinerProcess extends MinerProcess {

  implicit val mysqlDBConnector: MysqlDBConnector
  implicit val r: RScript
  val jdbcDriverAbsolutePath: String

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.impl.r.AprioriMinerProcess")
  private val rInitTemplateName = "RAprioriInit.mustache"
  private val rInitArulesTemplateName = "RAprioriInitArules.mustache"
  private val rInitAutoTemplateName = "RAprioriInitAuto.mustache"

  private class QueryBuilder {

    self: DatasetQueryBuilder =>

    private def sqlsToString(sqls: SQLSyntax): String = SqlUtils.stringifySqlSyntax(sqls)

    def toStrSqlConditions(exp: BoolExpression[Attribute]): String = sqlsToString(toSqlConditions(exp))

  }

  private class AttributeAppearanceValues(mt: MinerTask) {

    self: MinerDatasetOps with DatasetQueryBuilder =>

    val datasetDetail: DatasetDetail = mt.datasetDetail

    /**
      * Return select map for antecedent where key is colname and value is sql select part
      */
    private lazy val antecedentSql = mt.antecedent.map(toSqlConditions)
    /**
      * Return select map for consequent where key is colname and value is sql select part
      */
    private lazy val consequentSql = mt.consequent.map(toSqlConditions)

    /**
      * This stringify colname,value pair to "item"
      *
      * @param item some item
      * @return String
      */
    private def itemToAppearance(item: Item) = "\"" + ItemText(item) + "\""

    /**
      * This implicit method converts all Item List to String.
      * Where the list contains items and the final string will contain all items in one string
      *
      * @param items items list
      * @return String
      */
    implicit private def itemsToString(items: List[Item]): String = items.map(itemToAppearance).mkString(", ")

    /**
      * Get all items for antecedent separated by comma
      *
      * @return String
      */
    def antecedentAppearanceValues: String = antecedentSql.map[String](fetchItemsBySelect).getOrElse("")

    /**
      * Get all items for consequent separated by comma
      *
      * @return String
      */
    def consequentAppearanceValues: String = consequentSql.map[String](fetchItemsBySelect).getOrElse("")

    /**
      * Get all items for consequent minus antecedent separated by comma.
      * This result is used for rhs - consequent values, where lhs is default
      *
      * @return String
      */
    def consequentMinusAntecedentAppearanceValues: String = consequentSql.map[String] { consequentSql =>
      antecedentSql.map[String] { antecedentSql =>
        fetchComplementedItemsBySelects(consequentSql, antecedentSql)
      }.getOrElse(fetchItemsBySelect(consequentSql))
    }.getOrElse("")

    /**
      * Get all items for consequent and antecedent intersection separated by comma.
      * This result is used for both - antecedent and consequent values in both sides
      *
      * @return String
      */
    def antecedentIntersectConsequentAppearanceValues: String = antecedentSql.zip(consequentSql).headOption.map[String] {
      case (antecedentSql, consequentSql) => fetchIntersectedItemsBySelects(antecedentSql, consequentSql)
    }.getOrElse("")

  }

  private def init(mt: MinerTask): Unit = {
    val instanceTable = new InstanceTable(mt.datasetDetail.id)
    val queryBuilder = new QueryBuilder with MysqlQueryBuilder {
      val attributeColumn: scalikejdbc.SQLSyntax = instanceTable.column.attribute
      val itemColumn: scalikejdbc.SQLSyntax = instanceTable.column.value
    }
    logger.debug(s"New task was received: $mt")
    val im = mt.interestMeasures.getAll.foldLeft(Map(
      "jdbcDriverAbsolutePath" -> jdbcDriverAbsolutePath,
      "dbServer" -> mysqlDBConnector.dbSettings.dbServer,
      "dbName" -> mysqlDBConnector.dbSettings.dbName,
      "dbUser" -> mysqlDBConnector.dbSettings.dbUser,
      "dbPassword" -> mysqlDBConnector.dbSettings.dbPassword,
      "dbTableName" -> instanceTable.tableName
    ): Map[String, Any]) {
      case (m, Confidence(x)) => m + ("confidence" -> x)
      case (m, Support(x)) => m + ("support" -> x)
      case (m, Lift(x)) => m + ("lift" -> x)
      case (m, Limit(x)) => m + ("limit" -> x)
      case (m, CBA) => m + ("cba" -> true)
      case (m, _) => m
    }
    val (initSettings, rscriptMiner) = if (mt.interestMeasures.has(Auto)) {
      //Within AutoConfSupp the consequent columns must be separated and concatenated as the last column in the sql query
      val consequentAttribute = mt.consequent.get.toAttributeDetails.head
      //if AutoConfSupp has an antecedent then the all boolean expression (antecedent + consequent) will be extracted togather
      //if AutoConfSupp has the empty antecedent then all attributes are using within the sql query
      val whereQuery = mt.antecedent match {
        case Some(antecedent) => queryBuilder.toStrSqlConditions(antecedent OR mt.consequent.get)
        case None => "1"
      }
      val rscript = Template(rInitAutoTemplateName, im ++ Map("consequent" -> consequentAttribute.id))
      logger.debug("Automatic parameter determination is turned on with this consequent: " + mt.consequent.get)
      (im + ("whereQuery" -> whereQuery), rscript)
    } else {
      val _mysqlDBConnector: MysqlDBConnector = implicitly
      val appearanceValues = new AttributeAppearanceValues(mt) with MysqlMinerDatasetOps with MysqlQueryBuilder {
        val mysqlDBConnector: MysqlDBConnector = _mysqlDBConnector
        val attributeColumn: scalikejdbc.SQLSyntax = valueTable.column.attribute
        val itemColumn: scalikejdbc.SQLSyntax = valueTable.column.id
      }
      val (whereQuery, arulesSettings) = (mt.antecedent, mt.consequent) match {
        case (Some(antecedent), Some(consequent)) => queryBuilder.toStrSqlConditions(antecedent OR consequent) -> Map(
          "defaultAppearance" -> "lhs",
          "consequent" -> appearanceValues.consequentMinusAntecedentAppearanceValues,
          "both" -> appearanceValues.antecedentIntersectConsequentAppearanceValues
        ).filter(_._2.nonEmpty)
        case (Some(_), None) => "1" -> Map(
          "defaultAppearance" -> "rhs",
          "both" -> appearanceValues.antecedentAppearanceValues
        )
        case (None, Some(_)) => "1" -> Map(
          "defaultAppearance" -> "lhs",
          "both" -> appearanceValues.consequentAppearanceValues
        )
        case (None, None) => "1" -> Map(
          "defaultAppearance" -> "both"
        )
      }
      val rscript = Template(rInitArulesTemplateName, im ++ arulesSettings)
      logger.debug("Itemsets will be filtered by this SQL Select query: " + whereQuery)
      logger.debug("Default appearance is: " + arulesSettings("defaultAppearance"))
      logger.debug("Consequent values are: " + arulesSettings.get("consequent"))
      logger.debug("Both values are: " + arulesSettings.get("both"))
      (im + ("whereQuery" -> whereQuery), rscript)
    }
    val rscriptInit = Template(rInitTemplateName, initSettings)
    logger.trace("This initialization Rscript will be passed to the Rserve:\n" + rscriptInit)
    r.eval(rscriptInit)
    logger.trace("This mining Rscript will be passed to the Rserve:\n" + rscriptMiner)
    r.eval(rscriptMiner)
  }

  def process(mt: MinerTask)(processListener: (MinerResult) => Unit): MinerResult = {
    val startTime = System.currentTimeMillis()
    init(mt)
    val attributes = MysqlAttributeOps(mt.datasetDetail).getAllAttributes
    val incrementalMiner = new AprioriIncrementalMiner(mt, attributes)(processListener)
    val timePreparing = (System.currentTimeMillis() - startTime) millis
    val result = if (mt.interestMeasures.has(Auto)) {
      incrementalMiner.partialMine(1, if (mt.interestMeasures.hasType[MaxRuleLength]) incrementalMiner.maxlen else attributes.size, 0)
    } else if (mt.interestMeasures.has(CBA)) {
      incrementalMiner.partialMine(incrementalMiner.minlen, incrementalMiner.maxlen, 0)
    } else {
      incrementalMiner.incrementalMine
    }
    result.copy(headers = result.headers merge MiningTime(timePreparing, Duration.Zero, Duration.Zero))
  }

}