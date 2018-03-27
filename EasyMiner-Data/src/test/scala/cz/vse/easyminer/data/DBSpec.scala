package cz.vse.easyminer.data

import java.io.StringReader
import java.nio.charset.Charset
import java.util.Locale

import com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException
import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.ScriptRunner
import cz.vse.easyminer.core.{MysqlUserDatabase, TaskStatusProcessor}
import cz.vse.easyminer.data.impl.Validators
import cz.vse.easyminer.data.impl.db.ValidationDataSourceBuilder
import cz.vse.easyminer.data.impl.db.mysql.Tables._
import cz.vse.easyminer.data.impl.db.mysql._
import cz.vse.easyminer.data.impl.parser.{CsvInputParser, RdfInputParser}
import org.apache.jena.riot.Lang
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import cz.vse.easyminer.data.impl.db.DataSourceTypeConversions.Limited._
import scalikejdbc._

class DBSpec extends FlatSpec with Matchers with TemplateOpt with BeforeAndAfterAll {

  implicit val taskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor
  implicit val mysqlConnector = DBSpec.makeMysqlConnector(false)
  lazy val dataSourceOps = MysqlDataSourceOps()
  lazy val fieldOps = MysqlFieldOps(dataSourceOps.getDataSource(1).get)
  lazy val valueOps = MysqlValueOps(fieldOps.dataSource, fieldOps.getField(2).get)

  import mysqlConnector._

  def buildDatasource() = MysqlDataSourceBuilder("test").build { attrb =>
    attrb
      .field(Field("attribute nominal", NominalFieldType))
      .field(Field("attribute number", NumericFieldType))
      .build(_
        .addInstance(List(NominalValue("value 1"), NumericValue("15.123456", 15.123456)))
        .addInstance(List(NullValue, NullValue))
        .addInstance(List(NominalValue("value 1"), NumericValue("1", 1)))
        .addInstance(List(NominalValue("value 1"), NumericValue("2", 2)))
        .addInstance(List(NominalValue("value 2"), NumericValue("2", 2)))
        .build
      )
  }

  "Wrong scheme" should "throw exception" in intercept[MySQLSyntaxErrorException] {
    try {
      tryClose(DBConn.conn) { conn =>
        tryClose(new StringReader(template("metadataSchemaWithError.mustache", Map("prefix" -> Tables.tablePrefix))))(new ScriptRunner(conn, false, true).runScript)
      }
    } catch {
      case ex: Throwable =>
        DBSpec.rollbackData()
        throw ex
    }
  }

  "MysqlSchemaOps" should "create schema" in {
    val ops = new MysqlSchemaOps()
    ops.createSchema()
    ops.schemaExists shouldBe true
  }

  "Builder" should "create a data source with all properties" in {
    buildDatasource().size shouldBe 5
  }

  it should "throw validation exception if data source name is too long" in intercept[ValidationException] {
    MysqlDataSourceBuilder(Array.fill(Validators.tableNameMaxlen + 1)('a').mkString).build { attrb =>
      null
    }
  }

  it should "throw validation exception if colname attr is too long" in intercept[ValidationException] {
    MysqlDataSourceBuilder("test").build { attrb =>
      attrb
        .field(Field(Array.fill(Validators.tableColMaxlen + 1)('a').mkString, NominalFieldType))
        .build(_.dataSource)
      null
    }
  }

  it should "throw validation exception if colname value is too long" in intercept[ValidationException] {
    MysqlDataSourceBuilder("test").build { attrb =>
      attrb
        .field(Field("aaaaa", NominalFieldType))
        .build(_
          .addInstance(Seq(NominalValue(Array.fill(Validators.tableColMaxlen + 1)('a').mkString)))
          .build
        )
    }
  }

  it should "throw validation exception if empty data source name" in intercept[ValidationException] {
    MysqlDataSourceBuilder("").build { attrb =>
      null
    }
  }

  it should "throw validation exception if empty field name" in intercept[ValidationException] {
    MysqlDataSourceBuilder("aaa").build { attrb =>
      attrb
        .field(Field("", NominalFieldType))
        .build(_.dataSource)
      null
    }
  }

  it should "throw validation exception if empty instances" in intercept[ValidationDataSourceBuilder.Exceptions.DataSourceBuilderException] {
    MysqlDataSourceBuilder("aaa").build { attrb =>
      attrb
        .field(Field("aaa", NominalFieldType))
        .build(_
          .addInstance(Nil)
          .build
        )
    }
  }

  it should "throw validation exception if invalid values size" in intercept[ValidationDataSourceBuilder.Exceptions.DataSourceBuilderException] {
    MysqlDataSourceBuilder("aaa").build { attrb =>
      attrb
        .field(Field("aaa", NumericFieldType))
        .build(_
          .addInstance(List(NumericValue("10", 10), NominalValue("aaa")))
          .build
        )
    }
  }

  "DataSourceOps" should "return all data sources and all their properties within instances" in {
    dataSourceOps.getAllDataSources.size shouldBe 1
    dataSourceOps.getDataSource(1).map(_.name).getOrElse("") shouldBe "test"
    dataSourceOps.renameDataSource(1, "renamed")
    dataSourceOps.getDataSource(1).map(_.name).getOrElse("") shouldBe "renamed"
    val instances = dataSourceOps.getAggregatedInstances(1, Nil, 0, 1000)
    instances.size shouldBe 5
    fieldOps.getAllFields.size shouldBe 2
    (dataSourceOps.getAggregatedInstances(1, List(1), 0, 1) match {
      case Seq(AggregatedInstance(_, Seq(AggregatedInstanceItem(1, NominalValue(value))))) => value
      case _ => ""
    }) shouldBe "value 1"
    dataSourceOps.deleteDataSource(1)
    dataSourceOps.getAllDataSources shouldBe Nil
    DBSpec.rollbackData()
    new MysqlSchemaOps().createSchema()
    buildDatasource()
  }

  it should "return instances" in {
    for (dataSource <- dataSourceOps.getAllDataSources) {
      val inst = dataSourceOps.getAggregatedInstances(dataSource.id, Nil, 0, 1)
      inst.size shouldBe 1
      inst.foreach { instance =>
        instance.values.size shouldBe 2
        instance.values.map(_.value) should contain only(NominalValue("value 1"), NumericValue("15.123456", 15.123456))
        val field = instance.values.find(_.value.isInstanceOf[NominalValue]).get.field
        val inst = dataSourceOps.getInstances(dataSource.id, List(field), 0, 1000)
        inst.map(_.field) should contain only field
        inst.size shouldBe 4
        inst.map(_.value) should contain(NominalValue("value 1"))
      }
    }
  }

  it should "throw validation exception if empty renamed datasource" in intercept[ValidationException] {
    dataSourceOps.renameDataSource(1, "")
  }

  it should "throw validation exception if long renamed datasource" in intercept[ValidationException] {
    dataSourceOps.renameDataSource(1, Array.fill(Validators.tableNameMaxlen + 1)('a').mkString)
  }

  it should "do nothing if invalid ID during deleting" in {
    dataSourceOps.deleteDataSource(2)
    dataSourceOps.getAllDataSources.size shouldBe 1
  }

  it should "return None if invalid data source ID" in {
    dataSourceOps.getDataSource(2) shouldBe None
  }

  it should "return None instances if invalid data source ID" in {
    dataSourceOps.getInstances(2, Nil, 0, 1) shouldBe empty
  }

  it should "throw validation exception if bad offset" in intercept[ValidationException] {
    dataSourceOps.getInstances(1, Nil, -1, 1)
  }

  it should "throw validation exception if high limit" in intercept[ValidationException] {
    dataSourceOps.getInstances(1, Nil, 0, 1001)
  }

  it should "throw validation exception if low limit" in intercept[ValidationException] {
    dataSourceOps.getInstances(1, Nil, 0, 0)
  }

  "FieldOps" should "return all fields and their properties and do all ops" in {
    fieldOps.getAllFields.size shouldBe 2
    fieldOps.getField(1) shouldBe Some(FieldDetail(1, 1, "attribute nominal", NominalFieldType, 2, 0, 4, 0))
    fieldOps.renameField(1, "aaa")
    (fieldOps.getField(1) match {
      case Some(FieldDetail(_, _, "aaa", _, _, _, _, _)) => true
      case _ => false
    }) shouldBe true
    fieldOps.deleteField(1)
    fieldOps.getAllFields.size shouldBe 1
  }

  it should "throw validation exception if empty renamed field" in intercept[ValidationException] {
    fieldOps.renameField(2, "")
  }

  it should "throw validation exception if long renamed field" in intercept[ValidationException] {
    fieldOps.renameField(2, Array.fill(Validators.tableColMaxlen + 1)('a').mkString)
  }

  "ValueOps" should "return all values and their properties and do all ops" in {
    (valueOps.getValues(0, 1000) match {
      case Seq(NumericValueDetail(_, _, "1", 1, 1), NumericValueDetail(_, _, "2", 2, 2), NumericValueDetail(_, _, "15.123456", 15.123456, 1)) => true
      case _ => false
    }) shouldBe true
    (valueOps.getNumericStats match {
      case Some(x) =>
        x.max shouldBe 15.123456
        x.min shouldBe 1
        x.avg.floor shouldBe 5
        true
      case _ => false
    }) shouldBe true
    (valueOps.getHistogram(2) match {
      case Seq(NumericValueInterval(_, _, 3), NumericValueInterval(_, _, 1)) => true
      case _ => false
    }) shouldBe true
    (valueOps.getHistogram(2, Some(ExclusiveIntervalBorder(1.0)), Some(ExclusiveIntervalBorder(15.123456))) match {
      case Seq(NumericValueInterval(_, _, 2), NumericValueInterval(_, _, 0)) => true
      case _ => false
    }) shouldBe true
  }

  it should "throw validation exception if bad offset" in intercept[ValidationException] {
    valueOps.getValues(-1, 0)
  }

  it should "throw validation exception if high limit" in intercept[ValidationException] {
    valueOps.getValues(0, 1001)
  }

  it should "throw validation exception if low limit" in intercept[ValidationException] {
    valueOps.getValues(0, 0)
  }

  it should "throw validation exception if low number of bins" in intercept[ValidationException] {
    valueOps.getHistogram(1)
  }

  it should "throw validation exception if high number of bins" in intercept[ValidationException] {
    valueOps.getHistogram(1001)
  }

  it should "throw validation exception if min is higher or equal max border within numeric intervals" in intercept[ValidationException] {
    valueOps.getHistogram(2, Some(InclusiveIntervalBorder(2)), Some(InclusiveIntervalBorder(2)))
  }

  "DataSourceBuilder" should "process rdf file" in {
    val dataSourceBuilder = MysqlDataSourceBuilder("rdftest")
    val rdfParser = RdfInputParser(dataSourceBuilder, RdfInputParser.Settings(Lang.NT, None))(dataSourceDetail => MysqlRdfDataSource(dataSourceDetail))
    val dataSourceDetail = rdfParser.write(getClass.getResourceAsStream("/test.nt"))
    dataSourceDetail.size shouldBe 576
    dataSourceDetail.toDataSourceOps.getAggregatedInstances(dataSourceDetail.id, Nil, 0, 1000).size shouldBe 576
    val fieldOps = dataSourceDetail.toFieldOps(dataSourceDetail)
    val fields = fieldOps.getAllFields
    fields.size shouldBe 4
    dataSourceDetail.toValueOps(dataSourceDetail, fieldOps.getField(fields.find(_.name == "http://dbpedia.org/ontology/genre").get.id).get).getValues(0, 1000).size shouldBe 43
    val stats = dataSourceDetail.toValueOps(dataSourceDetail, fieldOps.getField(fields.find(_.`type` == NumericFieldType).get.id).get).getNumericStats.get
    stats.min shouldBe 8.0
    stats.max shouldBe 950.0
    stats.avg should be(93.0 +- 1.0)
  }

  "DataSource from CSV" should "be created" in {
    val csvParser = new CsvInputParser(
      MysqlDataSourceBuilder("csvtest"),
      CsvInputParser.Settings(
        ',',
        Charset.forName("UTF-8"),
        '"',
        '"',
        new Locale("cs"),
        None,
        Set("null", ""),
        List(None, Some(NumericFieldType), Some(NumericFieldType), Some(NominalFieldType), None, None, Some(NumericFieldType), Some(NominalFieldType))
      )
    )
    val dataSourceDetail = csvParser.write(getClass.getResourceAsStream("/test.csv"))
    dataSourceDetail should matchPattern { case DataSourceDetail(_, "csvtest", LimitedDataSourceType, 6181, true) => }
  }

  it should "change type" in {
    val dataSourceDetail = MysqlDataSourceOps().getAllDataSources.find(_.name == "csvtest").get
    val fieldOps = dataSourceDetail.toFieldOps(dataSourceDetail)
    fieldOps.getAllFields.collect {
      case districtField if districtField.name == "district" =>
        districtField.support shouldBe 6181
        districtField.uniqueValuesSize shouldBe 80
        fieldOps.changeFieldType(districtField.id) shouldBe true
        val newDistrictField = fieldOps.getField(districtField.id).get
        newDistrictField.support shouldBe 1
        newDistrictField.uniqueValuesSize shouldBe 1
        fieldOps.changeFieldType(districtField.id) shouldBe true
        1
      case ratingField if ratingField.name == "rating" =>
        fieldOps.changeFieldType(ratingField.id) shouldBe false
        1
    }.sum shouldBe 2
  }

  override protected def beforeAll(): Unit = DBSpec.rollbackData()

  override protected def afterAll(): Unit = {
    DBSpec.rollbackData()
    mysqlConnector.close()
  }

}

object DBSpec extends ConfOpt {

  def makeMysqlConnector(withNewSchema: Boolean = true) = {
    implicit val connector = new MysqlDBConnector(MysqlUserDatabase(dbserver, dbname, dbuser, dbpassword))
    if (withNewSchema) {
      rollbackData()
      val ops = new MysqlSchemaOps()
      if (!ops.schemaExists) ops.createSchema()
    }
    connector
  }

  def rollbackData()(implicit connector: MysqlDBConnector) = connector.DBConn autoCommit { implicit session =>
    sql"SHOW TABLES LIKE ${DataSourceTable.tableName}".map(_ => true).first().apply.foreach { _ =>
      val mysqlDataSourceOps = MysqlDataSourceOps()
      mysqlDataSourceOps.getAllDataSources.foreach(x => mysqlDataSourceOps.deleteDataSource(x.id))
    }
    sql"DROP TABLE IF EXISTS ${FieldNumericDetailTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${FieldTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${DataSourceTable.table}".execute().apply()
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