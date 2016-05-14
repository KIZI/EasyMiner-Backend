package cz.vse.easyminer.data

import java.nio.charset.Charset
import java.util.Locale

import cz.vse.easyminer.core.{TaskStatusProcessor, HiveUserDatabase}
import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.data.hadoop.DataHdfs
import cz.vse.easyminer.data.impl.db.hive._
import cz.vse.easyminer.data.impl.db.mysql.MysqlSchemaOps
import cz.vse.easyminer.data.impl.parser.CsvInputParser
import cz.vse.easyminer.data.impl.parser.CsvInputParser.Settings
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

/**
 * Created by propan on 12. 10. 2015.
 */
class HiveSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val taskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor
  implicit val mysqlConnector = DBSpec.makeMysqlConnector(false)
  implicit val hiveConnector = HiveSpec.makeHiveConnector

  val defaultCsvSettings = Settings(
    ',',
    Charset.forName("UTF-8"),
    '"',
    '"',
    new Locale("cs"),
    None,
    Set("null", ""),
    List(None, Some(NumericFieldType), None, Some(NominalFieldType), None, None, None, Some(NominalFieldType))
  )

  lazy val dataSourceOps = HiveDataSourceOps()

  override protected def beforeAll(): Unit = {
    val schemaOps = new MysqlSchemaOps()
    if (schemaOps.schemaExists) {
      HiveSpec.rollbackData()
      DBSpec.rollbackData()
    }
    schemaOps.createSchema()
  }

  "Hdfs object" should "upload a CSV file to HDFS and delete it" in {
    DataHdfs { hdfs =>
      val fileName = "test.csv"
      hdfs.useCsvWriter(fileName, ",", "~") { csvWriter =>
        csvWriter.writeLine(Seq(NominalValue("a"), NumericValue(1), NullValue))
        csvWriter.writeLine(Seq(NominalValue("b"), NumericValue(2), NullValue))
        csvWriter.writeLine(Seq(NominalValue("c"), NumericValue(3), NullValue))
      }
      hdfs.fileExists(fileName) shouldBe true
      hdfs.deleteFile(fileName)
      hdfs.fileExists(fileName) shouldBe false
    }
  }

  "Hive data source builder" should "upload a CSV document and save it to a Hive table including meta information" in {
    val builder = HiveDataSourceBuilder("test")
    val csvHandler = new CsvInputParser(builder, defaultCsvSettings)
    val detail = csvHandler.write(getClass.getResourceAsStream("/test.csv"))
    detail.`type` shouldBe UnlimitedDataSourceType
    detail.name shouldBe "test"
    detail.size shouldBe 6181
  }

  "Hive data source read/update ops" should "read and update data source/s" in {
    val dataSources = dataSourceOps.getAllDataSources
    dataSources.size shouldBe 1
    for (dataSource <- dataSources) {
      dataSourceOps.getDataSource(dataSource.id) shouldBe Some(DataSourceDetail(dataSource.id, "test", UnlimitedDataSourceType, 6181, true))
      dataSourceOps.renameDataSource(dataSource.id, "something")
      dataSourceOps.getDataSource(dataSource.id).map(_.name) shouldBe Some("something")
      dataSourceOps.renameDataSource(dataSource.id, dataSource.name)
      val fieldOps = HiveFieldOps(dataSource)
      val fieldsList = List(Nil, fieldOps.getAllFields.filter(_.name == "age").map(_.id))
      for (fields <- fieldsList) {
        val instances = dataSourceOps.getInstances(dataSource.id, fields, 0, 5).get
        val fieldsSize = if (fields.isEmpty) 3 else fields.size
        instances.fields.size shouldBe fieldsSize
        instances.instances.size shouldBe 5
        for ((instance, index) <- instances.instances.zipWithIndex) {
          instance.id shouldBe (index + 1)
          instance.values.size shouldBe fieldsSize
        }
      }
    }
  }

  "Hive field read/update ops" should "read and update fields" in {
    for (dataSource <- dataSourceOps.getAllDataSources) {
      val fieldOps = HiveFieldOps(dataSource)
      val fields = fieldOps.getAllFields
      fields.size shouldBe 3
      fields.flatMap(field => fieldOps.getField(field.id)).size shouldBe 3
      for (field <- fields) {
        List("age", "district", "rating") should contain(field.name)
        List(47, 79, 4) should contain(field.uniqueValuesSize)
        fieldOps.renameField(field.id, "something")
        fieldOps.getField(field.id).map(_.name) shouldBe Some("something")
        fieldOps.renameField(field.id, field.name)
      }
    }
  }

  "Hive field value ops" should "read values" in {
    for (dataSource <- dataSourceOps.getAllDataSources) {
      val fieldOps = HiveFieldOps(dataSource)
      val fields = fieldOps.getAllFields
      for (field <- fields) {
        val valueOps = HiveValueOps(dataSource, field)
        if (field.name == "rating") {
          valueOps.getValues(0, 5).collect {
            case NominalValueDetail(_, _, v, f) => v -> f
          } should contain only("A" -> 1827, "B" -> 289, "C" -> 3627, "D" -> 438)
        }
        if (field.name == "age") {
          val values = valueOps.getValues(1, 10).collect {
            case NumericValueDetail(_, _, v, _) => v
          }
          values.size shouldBe 10
          all(values) should be < 30.0
          val stats = valueOps.getNumericStats
          stats.isDefined shouldBe true
          for (FieldNumericDetail(_, min, max, avg) <- stats) {
            min shouldBe 20.0
            max shouldBe 65.0
            avg should (be > 41.0 and be < 50.0)
          }
          valueOps.getHistogram(5) should contain only(NullValueInterval(1),
            NumericValueInterval(InclusiveIntervalBorder(20.0), ExclusiveIntervalBorder(29.0), 1071),
            NumericValueInterval(InclusiveIntervalBorder(29.0), ExclusiveIntervalBorder(38.0), 1434),
            NumericValueInterval(InclusiveIntervalBorder(38.0), ExclusiveIntervalBorder(47.0), 1231),
            NumericValueInterval(InclusiveIntervalBorder(47.0), ExclusiveIntervalBorder(56.0), 1300),
            NumericValueInterval(InclusiveIntervalBorder(56.0), InclusiveIntervalBorder(65.0), 1144))
          valueOps.getHistogram(2, Some(ExclusiveIntervalBorder(25.0)), Some(InclusiveIntervalBorder(30.0))) should contain allOf(
            NumericValueInterval(ExclusiveIntervalBorder(25.0), ExclusiveIntervalBorder(27.5), 225),
            NumericValueInterval(InclusiveIntervalBorder(27.5), InclusiveIntervalBorder(30.0), 499))
        }
      }
    }
  }

  "Hive delete ops" should "delete fields and data sources" in {
    for (dataSource <- dataSourceOps.getAllDataSources) {
      val fieldOps = HiveFieldOps(dataSource)
      val fields = fieldOps.getAllFields
      for ((field, index) <- fields.zipWithIndex) {
        fieldOps.deleteField(field.id)
        fieldOps.getAllFields.size shouldBe (fields.size - index - 1)
      }
      dataSourceOps.deleteDataSource(dataSource.id)
    }
    dataSourceOps.getAllDataSources.size shouldBe 0
  }

  override protected def afterAll(): Unit = {
    HiveSpec.rollbackData()
    DBSpec.rollbackData()
    hiveConnector.close()
    mysqlConnector.close()
  }

}

object HiveSpec extends ConfOpt {

  def makeHiveConnector = new HiveDBConnector(HiveUserDatabase(hiveserver, hiveport, hivename, hiveuser))

  def rollbackData()(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector) = {
    val dataSourceOps = HiveDataSourceOps()
    for (dataSource <- dataSourceOps.getAllDataSources) {
      dataSourceOps.deleteDataSource(dataSource.id)
    }
  }

  def apply(f: (MysqlDBConnector, HiveDBConnector) => Unit): Unit = {
    implicit val hiveConnector = makeHiveConnector
    DBSpec { implicit mysqlConnector =>
      try {
        f(mysqlConnector, hiveConnector)
      } finally {
        rollbackData()
        hiveConnector.close()
      }
    }
  }

}
