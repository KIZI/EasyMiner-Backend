package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.DependencyChecker.{DependecyCheckerException, Runner}
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.{DependencyChecker, TaskStatusProcessor}
import cz.vse.easyminer.data
import cz.vse.easyminer.data.impl.db.mysql.{Tables => DataTables, MysqlDataSourceOps, MysqlDataSourceBuilder, MysqlFieldOps}
import cz.vse.easyminer.data.{Field, NominalFieldType, NominalValue}
import cz.vse.easyminer.miner._
import cz.vse.easyminer.miner.impl.r.AprioriMiner
import cz.vse.easyminer.preprocessing.impl.db.mysql.{Tables => PreprocessingTables, _}
import cz.vse.easyminer.preprocessing.{Dataset, SimpleAttribute}
import scalikejdbc._

import scala.language.implicitConversions


/**
 * Created by propan on 20. 9. 2015.
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
          MysqlSimpleAttributeBuilder(SimpleAttribute(field.name, field.id), datasetDetail).build
        }
        val attributeOps = MysqlAttributeOps(datasetDetail)
        val attributesMap = attributeOps.getAllAttributes.map(x => x.name -> x).toMap
        val valueMapperOps = MysqlValueMapperOps(datasetDetail).valueMapper(attributesMap.values.map(x => x -> Set[data.Value](NominalValue("1"), NominalValue("0"))).toMap)
        implicit def itemToFixedValue(item: (String, String)): Attribute = {
          val attribute = attributesMap(item._1)
          FixedValue(attribute, valueMapperOps.normalizedValue(attribute, NominalValue(item._2)).get)
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
          if (result.rules.size != 3) throw new DependecyCheckerException(this)
        }
      }
    } finally {
      mysqlDBConnector.DBConn autoCommit { implicit session =>
        for (dataset <- MysqlDatasetOps().getAllDatasets) {
          val prepValueTable = new PreprocessingTables.ValueTable(dataset.id)
          val prepInstanceTable = new PreprocessingTables.InstanceTable(dataset.id)
          val dataValueTable = new DataTables.ValueTable(dataset.dataSource)
          val dataInstanceTable = new DataTables.InstanceTable(dataset.dataSource)
          sql"DROP TABLE IF EXISTS ${prepValueTable.table}".execute().apply()
          sql"DROP TABLE IF EXISTS ${prepInstanceTable.table}".execute().apply()
          sql"DROP TABLE IF EXISTS ${dataValueTable.table}".execute().apply()
          sql"DROP TABLE IF EXISTS ${dataInstanceTable.table}".execute().apply()
        }
        for (datasource <- MysqlDataSourceOps().getAllDataSources) {
          val dataValueTable = new DataTables.ValueTable(datasource.id)
          val dataInstanceTable = new DataTables.InstanceTable(datasource.id)
          sql"DROP TABLE IF EXISTS ${dataValueTable.table}".execute().apply()
          sql"DROP TABLE IF EXISTS ${dataInstanceTable.table}".execute().apply()
        }
        sql"DROP TABLE IF EXISTS ${PreprocessingTables.AttributeNumericDetailTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${PreprocessingTables.AttributeTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${PreprocessingTables.DatasetTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${DataTables.FieldNumericDetailTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${DataTables.FieldTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${DataTables.DataSourceTable.table}".execute().apply()
      }
    }
  }

}

object RDependencyChecker