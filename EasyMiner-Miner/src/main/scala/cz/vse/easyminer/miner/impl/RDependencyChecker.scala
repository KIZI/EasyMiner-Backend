/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.DependencyChecker.{DependecyCheckerException, Runner}
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.{DependencyChecker, TaskStatusProcessor}
import cz.vse.easyminer.data
import cz.vse.easyminer.data.impl.db.mysql.{MysqlDataSourceBuilder, MysqlDataSourceOps, MysqlFieldOps}
import cz.vse.easyminer.data.{Field, NominalFieldType, NominalValue}
import cz.vse.easyminer.miner._
import cz.vse.easyminer.miner.impl.r.AprioriMiner
import cz.vse.easyminer.preprocessing.impl.db.mysql.{MysqlAttributeOps, MysqlDatasetBuilder, MysqlDatasetOps, MysqlSchemaOps, MysqlSimpleAttributeBuilder, MysqlValueMapperOps}
import cz.vse.easyminer.preprocessing.{Dataset, SimpleAttribute}

import scala.language.implicitConversions


/**
 * Created by Vaclav Zeman on 20. 9. 2015.
 */
class RDependencyChecker(implicit mysqlDBConnector: MysqlDBConnector) extends DependencyChecker[Nothing] {

  val innerDependencyCheckers: Option[(Nothing) => Runner] = None

  implicit val rcp: RConnectionPool = RConnectionPoolImpl.default
  implicit val taskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor

  def check(): Unit = RDependencyChecker.synchronized {
    try {
      val schemaOps = MysqlSchemaOps()
      if (!schemaOps.schemaExists) schemaOps.createSchema()
      RScript.evalTx { r =>
        val dataSourceBuilder = MysqlDataSourceBuilder("r-dependency-checker")
        val dataSourceDetail = dataSourceBuilder.build {
          _.field(Field("milk", NominalFieldType))
            .field(Field("bread", NominalFieldType))
            .field(Field("butter", NominalFieldType))
            .build { valueBuilder =>
              val data = List(
                List(1, 1, 0),
                List(0, 0, 1),
                List(0, 0, 0),
                List(1, 1, 1),
                List(0, 1, 0)
              )
              data.foldLeft(valueBuilder)(_ addInstance _.map(x => NominalValue(x.toString))).build
            }
        }
        val fieldOps = MysqlFieldOps(dataSourceDetail)
        val datasetDetail = MysqlDatasetBuilder(Dataset("r-dependency-checker-dataset", dataSourceDetail)).build
        for (field <- fieldOps.getAllFields) {
          MysqlSimpleAttributeBuilder(List(SimpleAttribute(field.name, field.id)), datasetDetail).build
        }
        val attributeOps = MysqlAttributeOps(datasetDetail)
        val attributesMap = attributeOps.getAllAttributes.map(x => x.name -> x).toMap
        val valueMapperOps = MysqlValueMapperOps(datasetDetail).valueMapper(attributesMap.values.map(x => x -> Set[data.NominalValue](NominalValue("1"), NominalValue("0"))).toMap)
        implicit def itemToFixedValue(item: (String, String)): Attribute = {
          val attribute = attributesMap(item._1)
          FixedValue(attribute, valueMapperOps.item(attribute, NominalValue(item._2)).get)
        }
        RScript evalTx { r =>
          val miner: Miner = new AprioriMiner(r) with MinerTaskValidatorImpl
          val result = miner.mine(
            MinerTask(
              datasetDetail,
              Some(Value[Attribute]("butter" -> "1") AND Value("bread" -> "1")),
              InterestMeasures(Support(0.1), Confidence(0.5), Limit(1000), MinRuleLength(1), MaxRuleLength(8)),
              Some(Value("milk" -> "1"))
            )
          )(_ => {})
          if (result.rules.size != 4) throw new DependecyCheckerException(this)
        }
      }
    } finally {
      val datasetOps = MysqlDatasetOps()
      val dataSourceOps = MysqlDataSourceOps()
      datasetOps.getAllDatasets.map(_.id).foreach(datasetOps.deleteDataset)
      dataSourceOps.getAllDataSources.map(_.id).foreach(dataSourceOps.deleteDataSource)
    }
  }

}

object RDependencyChecker