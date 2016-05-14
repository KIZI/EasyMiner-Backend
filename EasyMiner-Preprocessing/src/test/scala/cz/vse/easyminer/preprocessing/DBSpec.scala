package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.mysql.Tables.{DataSourceTable, FieldNumericDetailTable, FieldTable, InstanceTable => DataInstanceTable, ValueTable => DataValueTable}
import cz.vse.easyminer.data.impl.db.mysql.{MysqlDataSourceOps, MysqlFieldOps}
import cz.vse.easyminer.preprocessing.impl.Validators
import cz.vse.easyminer.preprocessing.impl.db.DbAttributeBuilder.Exceptions.FieldNotFound
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeNumericDetailTable, AttributeTable, DatasetTable, InstanceTable => PreprocessingInstanceTable, ValueTable => PreprocessingValueTable}
import cz.vse.easyminer.preprocessing.impl.db.mysql.{MysqlSchemaOps => PreprocessingMysqlSchemaOps, _}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import scala.concurrent.duration._
import scalikejdbc._

import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class DBSpec extends FlatSpec with Matchers with TemplateOpt with BeforeAndAfterAll with MysqlDataDbOps {

  implicit lazy val mysqlDBConnector: MysqlDBConnector = DBSpec.makeMysqlConnector(false)

  lazy val dataSourceOps = MysqlDataSourceOps()

  "MysqlSchemaOps" should "create schema" in {
    val schemaOps = PreprocessingMysqlSchemaOps()
    schemaOps.createSchema()
    schemaOps.schemaExists shouldBe true
  }

  "Data service" should "create data source" in {
    val dataSourceDetail = buildDatasource("test", getClass.getResourceAsStream("/test.csv"))
    dataSourceDetail.name shouldBe "test"
    dataSourceDetail.size shouldBe 6181
    val dataSources = dataSourceOps.getAllDataSources
    dataSources should contain only dataSourceDetail
  }

  "Dataset builder" should "create a dataset" in {
    for (dataSource <- dataSourceOps.getAllDataSources) {
      val datasetDetail = MysqlDatasetBuilder(Dataset("test-prep", dataSource)).build
      datasetDetail.name shouldBe "test-prep"
      datasetDetail.`type` shouldBe LimitedDatasetType
      datasetDetail.dataSource shouldBe dataSource.id
      datasetDetail.size shouldBe 6181
    }
  }

  it should "throw exception if an invalid name" in {
    for (dataSource <- dataSourceOps.getAllDataSources) {
      intercept[ValidationException] {
        MysqlDatasetBuilder(Dataset("", dataSource)).build
      }
      intercept[ValidationException] {
        MysqlDatasetBuilder(Dataset(Array.fill(Validators.tableNameMaxlen + 1)('a').mkString, dataSource)).build
      }
    }
  }

  "Simple attribute builder" should "create an attribute from a field" in {
    val attributeList: List[AttributeDetail] = for {
      datasetDetail <- MysqlDatasetOps().getAllDatasets
      dataSourceDetail <- MysqlDataSourceOps().getDataSource(datasetDetail.dataSource).toList
      fieldDetail <- MysqlFieldOps(dataSourceDetail).getAllFields
    } yield {
        val attributeDetail = MysqlSimpleAttributeBuilder(SimpleAttribute(fieldDetail.name + "-attr", fieldDetail.id), datasetDetail).build
        attributeDetail.`type` match {
          case NominalAttributeType => fieldDetail.`type` shouldBe NominalFieldType
          case NumericAttributeType => fieldDetail.`type` shouldBe NumericFieldType
        }
        attributeDetail.dataset shouldBe datasetDetail.id
        attributeDetail.field shouldBe fieldDetail.id
        attributeDetail.name shouldBe (fieldDetail.name + "-attr")
        attributeDetail.uniqueValuesSize shouldBe fieldDetail.uniqueValuesSize
        attributeDetail
      }
    attributeList.size shouldBe 3
  }

  it should "build attributes in parallel" in {
    import scala.concurrent.ExecutionContext.Implicits._
    for {
      datasetDetail <- MysqlDatasetOps().getAllDatasets
      dataSourceDetail <- MysqlDataSourceOps().getDataSource(datasetDetail.dataSource).toList
      fieldDetail <- MysqlFieldOps(dataSourceDetail).getAllFields if fieldDetail.`type` == NumericFieldType
    } {
      val futures = for (i <- 0 until 5) yield {
        Future {
          MysqlSimpleAttributeBuilder(SimpleAttribute(fieldDetail.name + "-attr-par" + i, fieldDetail.id), datasetDetail).build
        }
      }
      val attributeOps = MysqlAttributeOps(datasetDetail)
      val valueIds = futures.map(Await.result(_, 10 seconds)).flatMap { attributeDetail =>
        attributeDetail.uniqueValuesSize shouldBe fieldDetail.uniqueValuesSize
        val ids = MysqlValueOps(datasetDetail, attributeDetail).getValues(0, 1000).map(_.id)
        attributeOps.deleteAttribute(attributeDetail.id)
        ids
      }
      valueIds.size shouldBe valueIds.toSet.size
    }
  }

  it should "throw exception if a non existing field or invalid name" in {
    for (datasetDetail <- MysqlDatasetOps().getAllDatasets) {
      intercept[FieldNotFound] {
        MysqlSimpleAttributeBuilder(SimpleAttribute("attr", 0), datasetDetail).build
      }
      intercept[FieldNotFound] {
        MysqlSimpleAttributeBuilder(SimpleAttribute("attr", 1), datasetDetail.copy(dataSource = 0)).build
      }
      for {
        dataSourceDetail <- MysqlDataSourceOps().getDataSource(datasetDetail.dataSource).toList
        fieldDetail <- MysqlFieldOps(dataSourceDetail).getAllFields
      } {
        intercept[ValidationException] {
          MysqlSimpleAttributeBuilder(SimpleAttribute("", fieldDetail.id), datasetDetail).build
        }
        intercept[ValidationException] {
          MysqlSimpleAttributeBuilder(SimpleAttribute(Array.fill(Validators.tableColMaxlen + 1)('a').mkString, fieldDetail.id), datasetDetail).build
        }
      }
    }
  }

  "ValueOps" should "return attribute values" in {
    val performedTestList = for {
      datasetDetail <- MysqlDatasetOps().getAllDatasets
      attributeDetail <- MysqlAttributeOps(datasetDetail).getAllAttributes
    } yield {
        val valueOps = MysqlValueOps(datasetDetail, attributeDetail)
        if (attributeDetail.`type` == NumericAttributeType) {
          val numericStats = valueOps.getNumericStats.get
          numericStats.min shouldBe 20
          numericStats.max shouldBe 65
          numericStats.avg shouldBe 41.9 +- 0.2
          valueOps.getHistogram(5) should contain only(NullValueInterval(1),
            NumericValueInterval(InclusiveIntervalBorder(20.0), ExclusiveIntervalBorder(29.0), 1071),
            NumericValueInterval(InclusiveIntervalBorder(29.0), ExclusiveIntervalBorder(38.0), 1434),
            NumericValueInterval(InclusiveIntervalBorder(38.0), ExclusiveIntervalBorder(47.0), 1231),
            NumericValueInterval(InclusiveIntervalBorder(47.0), ExclusiveIntervalBorder(56.0), 1300),
            NumericValueInterval(InclusiveIntervalBorder(56.0), InclusiveIntervalBorder(65.0), 1144))
          valueOps.getHistogram(2, Some(ExclusiveIntervalBorder(25.0)), Some(InclusiveIntervalBorder(30.0))) should contain allOf(
            NumericValueInterval(ExclusiveIntervalBorder(25.0), ExclusiveIntervalBorder(27.5), 225),
            NumericValueInterval(InclusiveIntervalBorder(27.5), InclusiveIntervalBorder(30.0), 499))
          intercept[ValidationException] {
            valueOps.getHistogram(1)
          }
          intercept[ValidationException] {
            valueOps.getHistogram(2000)
          }
          1
        } else if (attributeDetail.uniqueValuesSize == 4) {
          val values = valueOps.getValues(0, 10)
          values.size shouldBe 4
          values.collect { case x: NominalValueDetail => (x.value, x.frequency) } should contain theSameElementsAs List("A" -> 1827, "B" -> 289, "C" -> 3627, "D" -> 438)
          valueOps.getValues(1, 1).map(_.asInstanceOf[NominalValueDetail].value) should contain only "B"
          1
        } else {
          valueOps.getValues(0, 1000).size shouldBe 79
          intercept[ValidationException] {
            valueOps.getValues(-1, 10)
          }
          intercept[ValidationException] {
            valueOps.getValues(0, 0)
          }
          intercept[ValidationException] {
            valueOps.getValues(0, 2000)
          }
          1
        }
      }
    performedTestList.sum shouldBe 3
  }

  "ValueMapperOps" should "map an original value to an indexed value and vice versa" in {
    def valueDetailToValue(valueDetail: ValueDetail) = valueDetail match {
      case x: NominalValueDetail => NominalValue(x.value)
      case x: NumericValueDetail => NumericValue(x.value)
      case _: NullValueDetail => NullValue
    }
    for (datasetDetail <- MysqlDatasetOps().getAllDatasets) {
      val valueMapperOps = MysqlValueMapperOps(datasetDetail)
      val valueDetails = MysqlAttributeOps(datasetDetail).getAllAttributes.map(attributeDetail => attributeDetail -> MysqlValueOps(datasetDetail, attributeDetail).getValues(0, 1000))
      val valueMapper = valueMapperOps.valueMapper(valueDetails.map(x => x._1 -> x._2.map(valueDetailToValue).toSet).toMap)
      val normalizedValueMapper = valueMapperOps.normalizedValueMapper(valueDetails.map(x => x._1 -> x._2.map(x => NormalizedValue(x.id)).toSet).toMap)
      for {
        (attributeDetail, values) <- valueDetails
        valueDetail <- values
      } {
        valueMapper.normalizedValue(attributeDetail, valueDetailToValue(valueDetail)) shouldBe Some(NormalizedValue(valueDetail.id))
        normalizedValueMapper.value(attributeDetail, NormalizedValue(valueDetail.id)) shouldBe Some(valueDetailToValue(valueDetail))
        valueMapper.normalizedValue(attributeDetail, NominalValue("nothing")) shouldBe None
        normalizedValueMapper.value(attributeDetail, NormalizedValue(0)) shouldBe None
      }
    }
  }

  "AttributeOps" should "perform all methods" in {
    val performedTestList = for (datasetDetail <- MysqlDatasetOps().getAllDatasets) yield {
      val attributeOps = MysqlAttributeOps(datasetDetail)
      attributeOps.getAllAttributes.size shouldBe 3
      for (attributeDetail <- attributeOps.getAllAttributes) {
        Some(attributeDetail) shouldBe attributeOps.getAttribute(attributeDetail.id)
        attributeOps.renameAttribute(attributeDetail.id, "new-name")
        attributeOps.getAttribute(attributeDetail.id).map(_.name) shouldBe Some("new-name")
        attributeOps.renameAttribute(attributeDetail.id, attributeDetail.name)
        intercept[ValidationException] {
          attributeOps.renameAttribute(attributeDetail.id, "")
        }
        intercept[ValidationException] {
          attributeOps.renameAttribute(attributeDetail.id, Array.fill(Validators.tableColMaxlen + 1)('a').mkString)
        }
        if (attributeDetail.name == "district-attr") {
          attributeOps.deleteAttribute(attributeDetail.id)
          attributeOps.getAllAttributes.size shouldBe 2
        }
      }
      1
    }
    performedTestList.sum shouldBe 1
  }

  "DatasetOps" should "perform all methods" in {
    val datasetOps = MysqlDatasetOps()
    datasetOps.getAllDatasets.size shouldBe 1
    for (datasetDetail <- datasetOps.getAllDatasets) yield {
      Some(datasetDetail) shouldBe datasetOps.getDataset(datasetDetail.id)
      datasetOps.renameDataset(datasetDetail.id, "new-name")
      datasetOps.getDataset(datasetDetail.id).map(_.name) shouldBe Some("new-name")
      intercept[ValidationException] {
        datasetOps.renameDataset(datasetDetail.id, "")
      }
      intercept[ValidationException] {
        datasetOps.renameDataset(datasetDetail.id, Array.fill(Validators.tableNameMaxlen + 1)('a').mkString)
      }
      datasetOps.deleteDataset(datasetDetail.id)
      datasetOps.getAllDatasets.size shouldBe 0
    }
  }

  override protected def beforeAll(): Unit = DBSpec.rollbackData()

  override protected def afterAll(): Unit = {
    DBSpec.rollbackData()
    mysqlDBConnector.close()
  }


}

object DBSpec extends ConfOpt {

  def makeMysqlConnector(withNewSchema: Boolean = true) = {
    implicit val connector = new MysqlDBConnector(mysqlUserDatabase)
    if (withNewSchema) {
      rollbackData()
      val schemaOps = PreprocessingMysqlSchemaOps()
      if (!schemaOps.schemaExists) schemaOps.createSchema()
    }
    connector
  }

  def rollbackData()(implicit connector: MysqlDBConnector) = connector.DBConn autoCommit { implicit session =>
    def dropInstanceTable(table: SQLSyntaxSupport[_], tableIdColumn: SQLSyntax, tableIdToIntanceTable: Int => SQLSyntaxSupport[_], tableIdToValueTable: Int => SQLSyntaxSupport[_]) = sql"SHOW TABLES LIKE ${table.tableName}".map(_ => true).first().apply match {
      case Some(true) =>
        sql"SELECT $tableIdColumn FROM ${table.table}".foreach { rs =>
          val dataSourceId = rs.int(tableIdColumn.value)
          sql"DELETE FROM ${table.table} WHERE $tableIdColumn = $dataSourceId".execute().apply()
          val dataTable = tableIdToIntanceTable(dataSourceId)
          val valueTable = tableIdToValueTable(dataSourceId)
          sql"DROP TABLE IF EXISTS ${dataTable.table}".execute().apply()
          sql"DROP TABLE IF EXISTS ${valueTable.table}".execute().apply()
        }
      case _ =>
    }
    dropInstanceTable(DataSourceTable, DataSourceTable.column.id, id => new DataInstanceTable(id), id => new DataValueTable(id))
    sql"DROP TABLE IF EXISTS ${FieldNumericDetailTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${FieldTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${DataSourceTable.table}".execute().apply()
    dropInstanceTable(DatasetTable, DatasetTable.column.id, id => new PreprocessingInstanceTable(id), id => new PreprocessingValueTable(id))
    sql"DROP TABLE IF EXISTS ${AttributeNumericDetailTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${AttributeTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${DatasetTable.table}".execute().apply()
  }

  def apply(f: MysqlDBConnector => Unit): Unit = {
    implicit val connector = makeMysqlConnector()
    try {
      f(connector)
    } finally {
      rollbackData()
      connector.close()
    }
  }

}