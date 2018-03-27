package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.core.PersistentLock.Exceptions.LockedContext
import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.mysql.Tables.{DataSourceTable, FieldNumericDetailTable, FieldTable, InstanceTable => DataInstanceTable, ValueTable => DataValueTable}
import cz.vse.easyminer.data.impl.db.mysql.{MysqlDataSourceOps, MysqlFieldOps}
import cz.vse.easyminer.preprocessing.impl.Validators
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeTable, DatasetTable, InstanceTable => PreprocessingInstanceTable, ValueTable => PreprocessingValueTable}
import cz.vse.easyminer.preprocessing.impl.db.mysql.{MysqlSchemaOps => PreprocessingMysqlSchemaOps, _}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import scalikejdbc._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Try}

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
    val dataSources = dataSourceOps.getAllDataSources.filter(_.`type` == LimitedDataSourceType)
    dataSources should contain only dataSourceDetail
  }

  "Dataset builder" should "create a dataset" in {
    for (dataSource <- dataSourceOps.getAllDataSources if dataSource.`type` == LimitedDataSourceType) {
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
      datasetDetail <- MysqlDatasetOps().getAllDatasets if datasetDetail.`type` == LimitedDatasetType
      dataSourceDetail <- MysqlDataSourceOps().getDataSource(datasetDetail.dataSource).toList
      fieldDetail <- MysqlFieldOps(dataSourceDetail).getAllFields
    } yield {
      val attributeDetail = MysqlSimpleAttributeBuilder(List(SimpleAttribute(fieldDetail.name + "-attr", fieldDetail.id, Nil)), datasetDetail).build.head
      attributeDetail.dataset shouldBe datasetDetail.id
      attributeDetail.field shouldBe fieldDetail.id
      attributeDetail.name shouldBe (fieldDetail.name + "-attr")
      attributeDetail.uniqueValuesSize shouldBe fieldDetail.uniqueValuesSize
      attributeDetail
    }
    attributeList.size shouldBe 4
  }

  it should "build attributes in parallel" in {
    import cz.vse.easyminer.core.util.Concurrency._

    import scala.concurrent.ExecutionContext.Implicits._
    for {
      datasetDetail <- MysqlDatasetOps().getAllDatasets
      dataSourceDetail <- MysqlDataSourceOps().getDataSource(datasetDetail.dataSource).toList
      fieldDetail <- MysqlFieldOps(dataSourceDetail).getAllFields if fieldDetail.`type` == NumericFieldType
    } {
      val futures = for (i <- 0 until 5) yield {
        Future {
          MysqlSimpleAttributeBuilder(List(SimpleAttribute(fieldDetail.name + "-attr-par" + i, fieldDetail.id, Nil)), datasetDetail).build.head
        }
      }
      val attributeOps = MysqlAttributeOps(datasetDetail)
      val futureResults = futures.map(x => Try(x.quickResult))
      futureResults.collect {
        case Failure(_: LockedContext) => 1
      }.sum shouldBe 4
      for {
        futureResult <- futureResults
        attributeDetail <- futureResult
      } {
        val ids = MysqlValueOps(datasetDetail, attributeDetail).getValues(0, 1000).map(_.id)
        ids.size shouldBe ids.toSet.size
        attributeOps.deleteAttribute(attributeDetail.id)
        ids
      }
    }
  }

  it should "throw exception if a non existing field or invalid name" in {
    for (datasetDetail <- MysqlDatasetOps().getAllDatasets) {
      intercept[ValidationException] {
        MysqlSimpleAttributeBuilder(List(SimpleAttribute("attr", 0, Nil)), datasetDetail).build
      }
      intercept[ValidationException] {
        MysqlSimpleAttributeBuilder(List(SimpleAttribute("attr", 1, Nil)), datasetDetail.copy(dataSource = 0)).build
      }
      for {
        dataSourceDetail <- MysqlDataSourceOps().getDataSource(datasetDetail.dataSource).toList
        fieldDetail <- MysqlFieldOps(dataSourceDetail).getAllFields
      } {
        intercept[ValidationException] {
          MysqlSimpleAttributeBuilder(List(SimpleAttribute("", fieldDetail.id, Nil)), datasetDetail).build
        }
        intercept[ValidationException] {
          MysqlSimpleAttributeBuilder(List(SimpleAttribute(Array.fill(Validators.tableColMaxlen + 1)('a').mkString, fieldDetail.id, Nil)), datasetDetail).build
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
      if (attributeDetail.uniqueValuesSize == 4) {
        val values = valueOps.getValues(0, 10)
        values.size shouldBe 4
        values.map { x => (x.value, x.frequency) } should contain theSameElementsAs List("A" -> 1827, "B" -> 289, "C" -> 3627, "D" -> 438)
        valueOps.getValues(1, 1).map(_.value) should contain only "B"
        1
      } else if (attributeDetail.name.startsWith("salary")) {
        valueOps.getValues(0, 1000).size shouldBe 76
        1
      } else if (attributeDetail.name.startsWith("age")) {
        valueOps.getValues(0, 1000).size shouldBe 46
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
    performedTestList.sum shouldBe 4
  }

  "ValueMapperOps" should "map an original value to an indexed value and vice versa" in {
    def valueDetailToValue(valueDetail: ValueDetail) = NominalValue(valueDetail.value)
    for (datasetDetail <- MysqlDatasetOps().getAllDatasets) {
      val valueMapperOps = MysqlValueMapperOps(datasetDetail)
      val valueDetails = MysqlAttributeOps(datasetDetail).getAllAttributes.map(attributeDetail => attributeDetail -> MysqlValueOps(datasetDetail, attributeDetail).getValues(0, 1000))
      val valueMapper = valueMapperOps.valueMapper(valueDetails.map(x => x._1 -> x._2.map(valueDetailToValue).toSet[NominalValue]).toMap)
      val normalizedValueMapper = valueMapperOps.itemMapper(valueDetails.map(x => x._1 -> x._2.map(x => x.id).toSet).toMap)
      for {
        (attributeDetail, values) <- valueDetails
        valueDetail <- values
      } {
        valueMapper.item(attributeDetail, valueDetailToValue(valueDetail)) shouldBe Some(valueDetail.id)
        normalizedValueMapper.value(attributeDetail, valueDetail.id) shouldBe Some(valueDetailToValue(valueDetail))
        valueMapper.item(attributeDetail, NominalValue("nothing")) shouldBe None
        normalizedValueMapper.value(attributeDetail, 0) shouldBe None
      }
    }
  }

  "AttributeOps" should "perform all methods" in {
    val performedTestList = for (datasetDetail <- MysqlDatasetOps().getAllDatasets) yield {
      val attributeOps = MysqlAttributeOps(datasetDetail)
      attributeOps.getAllAttributes.size shouldBe 4
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
          attributeOps.getAllAttributes.size shouldBe 3
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
    //DBSpec.rollbackData()
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