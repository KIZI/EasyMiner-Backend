package cz.vse.easyminer.miner.impl.r

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.Template
import cz.vse.easyminer.miner.impl.MysqlMinerDatasetOps
import cz.vse.easyminer.miner.{Attribute, _}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlAttributeOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{InstanceTable, ValueTable}
import org.slf4j.LoggerFactory
import scalikejdbc.interpolation.SQLSyntax

import scala.language.implicitConversions

/**
  * Created by propan on 20. 11. 2015.
  */
trait AprioriMinerProcess extends MinerProcess {

  self: DatasetQueryBuilder =>

  implicit val mysqlDBConnector: MysqlDBConnector
  implicit val r: RScript
  val jdbcDriverAbsolutePath: String

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.impl.r.AprioriMinerProcess")
  private val rInitTemplateName = "RAprioriInit.mustache"
  private val rInitArulesTemplateName = "RAprioriInitArules.mustache"
  private val rInitAutoTemplateName = "RAprioriInitAuto.mustache"

  private class AttributeAppearanceValues(mt: MinerTask)(implicit instanceTable: InstanceTable) {

    self: MinerDatasetOps =>

    val datasetDetail: DatasetDetail = mt.datasetDetail

    private val valueTable = new ValueTable(mt.datasetDetail.id)

    implicit private def attributeDetailToColName(attributeDetail: AttributeDetail): SQLSyntax = valueTable.column.id

    /**
      * Return select map for antecedent where key is colname and value is sql select part
      */
    private lazy val antecedentSqlSelectMap = mt.antecedent.map(toSQLSelectMap).getOrElse(Map.empty)
    /**
      * Return select map for consequent where key is colname and value is sql select part
      */
    private lazy val consequentSqlSelectMap = mt.consequent.map(toSQLSelectMap).getOrElse(Map.empty)

    /**
      * This stringify colname,value pair to "colname=value"
      *
      * @param attributeDetail attribute
      * @param value           some value of the column
      * @return String
      */
    private def colValueToAppearance(attributeDetail: AttributeDetail)(value: Int) = "\"" + instanceTable.columnById(attributeDetail.id).value + "=" + value + "\""

    /**
      * This converts a select map from the query builder to List of "colname=value" pair.
      * Values are fetched for each key of map where key is colname, value is sql select part.
      *
      * @param selectMap key is colname and value is an sql select part for this colname
      * @return colname=value list
      */
    private def sqlSelectMapToAppearanceValues(selectMap: Map[AttributeDetail, SQLSyntax]) = selectMap.flatMap {
      case (k, v) => fetchValuesBySelectAndAttribute(k, v) map colValueToAppearance(k)
    }

    /**
      * This implicit method converts all String Seq to String.
      * Maps from query builder where key is colname and value is sql select part are flatMapped to a specific col=value List by a mode.
      * Then the List is converted to a String.
      * The String contains col=value list separated by comma.
      *
      * @param appearanceValues col=value list
      * @return String
      */
    implicit private def appearanceValuesToString(appearanceValues: Iterable[String]): String = appearanceValues.mkString(", ")

    /**
      * Get all "colname=value" items for antecedent separated by comma
      *
      * @return String
      */
    def antecedentAppearanceValues: String = sqlSelectMapToAppearanceValues(antecedentSqlSelectMap)

    /**
      * Get all "colname=value" items for consequent separated by comma
      *
      * @return String
      */
    def consequentAppearanceValues: String = sqlSelectMapToAppearanceValues(consequentSqlSelectMap)

    /**
      * Get all "colname=value" items for consequent minus antecedent separated by comma.
      * For each consequent colname are fetched values minus antecedent values for the same colname.
      * This result is used for rhs - consequent values, where lhs is default
      *
      * @return String
      */
    def consequentMinusAntecedentAppearanceValues: String = consequentSqlSelectMap.flatMap {
      case (k, conSel) =>
        val values = antecedentSqlSelectMap.get(k) match {
          case Some(antSel) => fetchComplementedValuesBySelectsAndAttribute(k, conSel, antSel)
          case None => fetchValuesBySelectAndAttribute(k, conSel)
        }
        values map colValueToAppearance(k)
    }

    /**
      * Get all "colname=value" items for consequent and antecedent intersection separated by comma.
      * For each consequent colname are fetched values intersecting antecedent values for the same colname.
      * This result is used for both - antecedent and consequent values in both sides
      *
      * @return String
      */
    def antecedentIntersectConsequentAppearanceValues: String = consequentSqlSelectMap.flatMap {
      case (k, conSel) =>
        val values = antecedentSqlSelectMap.get(k) match {
          case Some(antSel) => fetchIntersectedValuesBySelectsAndAttribute(k, conSel, antSel)
          case None => Nil
        }
        values map colValueToAppearance(k)
    }

  }

  /**
    * Implicit usage: toSQLSelect
    */
  implicit private def attributeDetailToColName(attributeDetail: AttributeDetail)(implicit instanceTable: InstanceTable): SQLSyntax = instanceTable.columnById(attributeDetail.id)

  private def expToSelectQuery(exp: BoolExpression[Attribute])(implicit instanceTable: InstanceTable) = {
    def stringifySqlSyntax(sqlSyntax: SQLSyntax) = {
      val params = sqlSyntax.parameters.toIterator
      sqlSyntax.value.foldLeft("") { (s, c) =>
        if (c == '?' && params.hasNext) {
          s + params.next().asInstanceOf[Int]
        } else {
          s + c
        }
      }
    }
    toSQLSelect(exp).map(stringifySqlSyntax).mkString(", ")
  }

  private def init(mt: MinerTask, attributes: Seq[AttributeDetail]): Unit = {
    implicit val instanceTable = new InstanceTable(mt.datasetDetail.id, attributes.map(_.id))
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
      val consequentColName = toSQLSelectMap(mt.consequent.get).keys.head.value
      val rscript = Template(rInitAutoTemplateName, im ++ Map("consequent" -> consequentColName))
      val attributeColNames = (attributes.iterator.map(attribute => instanceTable.columnById(attribute.id).value).filter(_ != consequentColName) ++ Iterator(expToSelectQuery(mt.consequent.get))).mkString(", ")
      logger.debug("Automatic parameter determination is turned on with this consequent: " + mt.consequent.get)
      (im + ("selectQuery" -> attributeColNames), rscript)
    } else {
      val _mysqlDBConnector: MysqlDBConnector = implicitly
      val appearanceValues = new AttributeAppearanceValues(mt) with MysqlMinerDatasetOps {
        val mysqlDBConnector: MysqlDBConnector = _mysqlDBConnector
      }
      val (selectQuery, arulesSettings) = (mt.antecedent, mt.consequent) match {
        case (Some(antecedent), Some(consequent)) => expToSelectQuery(antecedent OR consequent) -> Map(
          "defaultAppearance" -> "lhs",
          "consequent" -> appearanceValues.consequentMinusAntecedentAppearanceValues,
          "both" -> appearanceValues.antecedentIntersectConsequentAppearanceValues
        ).filter(_._2.nonEmpty)
        case (Some(_), None) => "*" -> Map(
          "defaultAppearance" -> "rhs",
          "both" -> appearanceValues.antecedentAppearanceValues
        )
        case (None, Some(_)) => "*" -> Map(
          "defaultAppearance" -> "lhs",
          "both" -> appearanceValues.consequentAppearanceValues
        )
        case (None, None) => "*" -> Map(
          "defaultAppearance" -> "both"
        )
      }
      val rscript = Template(rInitArulesTemplateName, im ++ arulesSettings)
      logger.debug("Itemsets will be filtered by this SQL Select query: " + selectQuery)
      logger.debug("Default appearance is: " + arulesSettings("defaultAppearance"))
      logger.debug("Consequent values are: " + arulesSettings.get("consequent"))
      logger.debug("Both values are: " + arulesSettings.get("both"))
      (im + ("selectQuery" -> selectQuery), rscript)
    }
    val rscriptInit = Template(rInitTemplateName, initSettings)
    logger.trace("This initialization Rscript will be passed to the Rserve:\n" + rscriptInit)
    r.eval(rscriptInit)
    logger.trace("This mining Rscript will be passed to the Rserve:\n" + rscriptMiner)
    r.eval(rscriptMiner)
  }

  def process(mt: MinerTask)(processListener: (MinerResult) => Unit): MinerResult = {
    val attributes = MysqlAttributeOps(mt.datasetDetail).getAllAttributes
    init(mt, attributes)
    val incrementalMiner = new AprioriIncrementalMiner(mt, attributes)(processListener)
    if (mt.interestMeasures.has(Auto)) {
      incrementalMiner.partialMine(1, attributes.size, 0)
    } else if (mt.interestMeasures.has(CBA)) {
      incrementalMiner.partialMine(incrementalMiner.minlen, incrementalMiner.maxlen, 0)
    } else {
      incrementalMiner.incrementalMine
    }
  }

}