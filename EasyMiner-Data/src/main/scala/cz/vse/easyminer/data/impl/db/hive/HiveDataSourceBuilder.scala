package cz.vse.easyminer.data.impl.db.hive

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.hadoop.DataHdfs
import cz.vse.easyminer.data.hadoop.DataHdfs.CsvWriter
import cz.vse.easyminer.data.impl.db.hive.{Tables => HiveTables}
import cz.vse.easyminer.data.impl.db.mysql.{Tables => MysqlTables}
import cz.vse.easyminer.data.impl.db.{DbFieldBuilder, ValidationDataSourceBuilder}
import scalikejdbc._
import cz.vse.easyminer.core.util.MapOps._

/**
  * Created by propan on 31. 10. 2015.
  */
class HiveDataSourceBuilder private[db](val name: String)(implicit hiveDBConnector: HiveDBConnector, mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) extends DataSourceBuilder {

  private val hiveTableDelimiter = ";"
  private val hiveTableEscape = """\"""

  class HiveValueBuilder(val dataSource: DataSourceDetail,
                         val fields: Seq[FieldDetail],
                         fieldMap: Map[Int, FieldDetail],
                         stats: Map[Int, FieldNumericDetail])
                        (implicit dataTableWriter: DataTableWriter) extends ValueBuilder {

    def this(dataSource: DataSourceDetail, fields: Seq[FieldDetail])(implicit dataTableWriter: DataTableWriter) = this(
      dataSource,
      fields,
      fields.iterator.map(fieldDetail => fieldDetail.id -> fieldDetail).toMap,
      Map.empty
    )

    def addTransaction(itemset: Set[(FieldDetail, Value)]): ValueBuilder = {
      for ((fieldDetail, value) <- itemset) {
        dataTableWriter.addRow(dataSource.size + 1, fieldDetail, value)
      }
      val newStats = itemset.foldLeft(stats) {
        case (stats, (fieldDetail, NumericValue(original, value))) => stats.applyAndUpdateOrElse(fieldDetail.id, FieldNumericDetail(fieldDetail.id, value, value, value))(fnd => fnd.copy(min = math.min(fnd.min, value), max = math.max(fnd.max, value), avg = (fnd.avg * dataSource.size + value) / (dataSource.size + 1)))
        case (stats, _) => stats
      }
      val newDataSource = dataSource.copy(size = dataSource.size + 1)
      if (newDataSource.size % 1000 == 0) taskStatusProcessor.newStatus("The data source is now populating by uploaded instances... Inserted rows: " + newDataSource.size)
      new HiveValueBuilder(
        newDataSource,
        fields,
        itemset.foldLeft(fieldMap) {
          case (fieldMap, (fieldDetail, _: NumericValue)) => fieldMap.applyAndUpdate(fieldDetail.id)(fieldDetail => fieldDetail.copy(supportNominal = fieldDetail.supportNominal + 1, supportNumeric = fieldDetail.supportNumeric + 1))
          case (fieldMap, (fieldDetail, _: NominalValue)) => fieldMap.applyAndUpdate(fieldDetail.id)(fieldDetail => fieldDetail.copy(supportNominal = fieldDetail.supportNominal + 1))
          case _ => fieldMap
        },
        newStats
      )
    }

    def build: DataSourceDetail = {
      taskStatusProcessor.newStatus("Meta information about the data source are updating...")
      mysqlDBConnector.DBConn autoCommit { implicit session =>
        sql"UPDATE ${MysqlTables.DataSourceTable.table} SET ${MysqlTables.DataSourceTable.column.size} = ${dataSource.size} WHERE ${MysqlTables.DataSourceTable.column.id} = ${dataSource.id}".execute().apply()
        for (fnd <- stats.values) {
          sql"INSERT INTO ${MysqlTables.FieldNumericDetailTable.table} (${MysqlTables.FieldNumericDetailTable.column.columns}) VALUES (${fnd.id}, ${fnd.min}, ${fnd.max}, ${fnd.avg})".execute().apply()
        }
        for (fieldDetail <- fieldMap.values) {
          sql"UPDATE ${MysqlTables.FieldTable.table} SET ${MysqlTables.FieldTable.column.field("supportNominal")} = ${fieldDetail.supportNominal}, ${MysqlTables.FieldTable.column.field("supportNumeric")} = ${fieldDetail.supportNumeric} WHERE ${MysqlTables.FieldTable.column.id} = ${fieldDetail.id}".execute().apply()
        }
      }
      dataSource
    }

  }

  class HiveFieldBuilder(val dataSource: DataSourceDetail, val fields: Vector[Field] = Vector())(implicit dataTable: DataTable) extends DbFieldBuilder {

    val connector: MysqlDBConnector = implicitly[MysqlDBConnector]

    def field(field: Field): FieldBuilder = new HiveFieldBuilder(dataSource, fields :+ field)

    def build(f: (ValueBuilder) => DataSourceDetail): DataSourceDetail = {
      val fieldsDetail = buildFields
      taskStatusProcessor.newStatus("The data source is now populating by uploaded instances...")
      val dataSourceDetail = dataTable.useWriter(fieldsDetail) { implicit dataTableWriter =>
        f(new HiveValueBuilder(dataSource, fieldsDetail))
      }
      dataTable.buildTable(dataSourceDetail, fieldsDetail)
      dataSourceDetail
    }

  }

  trait DataTable {
    def clean()

    def useWriter[T](fields: Seq[FieldDetail])(f: DataTableWriter => T): T

    def buildTable(dataSourceDetail: DataSourceDetail, fields: Seq[FieldDetail])
  }

  trait DataTableWriter {
    def addRow(id: Int, fieldDetail: FieldDetail, value: Value)
  }

  class InstanceDataTable(fileNamePrefix: String, fileNameSuffix: String)(implicit hdfs: DataHdfs) extends DataTable {

    class InstanceDataTableWriter(csvWriters: Map[Int, CsvWriter]) extends DataTableWriter {

      def addRow(id: Int, fieldDetail: FieldDetail, value: Value): Unit = {
        val params = value match {
          case value: NominalValue => List(NumericValue("", id), value, NullValue)
          case value: NumericValue => List(NumericValue("", id), NominalValue(value.original), value)
          case _ => Nil
        }
        if (params.nonEmpty) csvWriters(fieldDetail.id).writeLine(params)
      }

    }

    private def fieldToFileName(fieldDetail: FieldDetail) = s"$fileNamePrefix-${fieldDetail.id}$fileNameSuffix"

    def useWriter[T](fields: Seq[FieldDetail])(f: (DataTableWriter) => T): T = hdfs.useCsvWriters(hiveTableDelimiter, hiveTableEscape, fields map fieldToFileName: _*) { implicit csvWriters =>
      f(new InstanceDataTableWriter(fields.iterator.map(_.id).zip(csvWriters.iterator).toMap))
    }

    def buildTable(dataSourceDetail: DataSourceDetail, fields: Seq[FieldDetail]): Unit = hiveDBConnector.DBConn autoCommit { implicit session =>
      //prepare meta information about hive tables
      val narrowInstanceTable = new HiveTables.InstanceTable(dataSourceDetail.id)
      val nit = narrowInstanceTable.syntax("nit")
      val valueTable = new HiveTables.ValueTable(dataSourceDetail.id)
      taskStatusProcessor.newStatus("All uploaded instances are loading into a narrow table...")
      //create a hive table for uploaded CSV document with all columns
      sql"CREATE TABLE ${narrowInstanceTable.table} (${narrowInstanceTable.column.id} BIGINT, ${narrowInstanceTable.column.valueNominal} STRING, ${narrowInstanceTable.column.valueNumeric} DOUBLE) PARTITIONED BY (${narrowInstanceTable.column.field("field")} INT) ROW FORMAT DELIMITED FIELDS TERMINATED BY $hiveTableDelimiter ESCAPED BY ${hiveTableEscape + hiveTableEscape} STORED AS TEXTFILE".execute().apply()
      //create a hive table with information about all values, within uploaded data, and their frequencies
      //this table is useful to display histograms and basic statistics about data
      sql"CREATE TABLE ${valueTable.table} (${valueTable.column.valueNominal} STRING, ${valueTable.column.valueNumeric} DOUBLE, ${valueTable.column.frequency} INT, ${valueTable.column.rank} INT) PARTITIONED BY (${valueTable.column.field("field")} INT) STORED AS ORC".execute().apply()
      //load uploaded CSV data to the created table
      for (field <- fields) {
        val filePath = hdfs.filePath(fieldToFileName(field))
        sql"LOAD DATA INPATH ${filePath.toString} INTO TABLE ${narrowInstanceTable.table} PARTITION (${narrowInstanceTable.column.field("field")} = ${field.id})".execute().apply()
      }
      taskStatusProcessor.newStatus("Aggregated values and stats are now creating...")
      sql"SET hive.exec.dynamic.partition=true".execute().apply()
      sql"SET hive.exec.dynamic.partition.mode=nonstrict".execute().apply()
      sql"FROM ${narrowInstanceTable as nit} INSERT INTO TABLE ${valueTable.table} PARTITION (${valueTable.column.field("field")}) SELECT ${nit.valueNominal}, ${nit.valueNumeric}, COUNT(*), ROW_NUMBER() OVER (PARTITION BY ${nit.field("field")} ORDER BY ${nit.valueNumeric}, ${nit.valueNominal}), ${nit.field("field")} GROUP BY ${nit.field("field")}, ${nit.valueNumeric}, ${nit.valueNominal}".execute().apply() // DISTRIBUTY BY ${valueTable.column.field("field")} SORT BY ${valueTable.column.field("field")}, ${valueTable.column.valueNominal}, ${valueTable.column.valueNumeric}
      //next query computes number of values for each column
      taskStatusProcessor.newStatus("Number of unique values for each field is now counting...")
      val freqMap = sql"SELECT ${valueTable.column.field("field")}, isnull(${valueTable.column.valueNumeric}) AS nullnum, COUNT(*) AS count FROM ${valueTable.table} GROUP BY ${valueTable.column.field("field")}, isnull(${valueTable.column.valueNumeric})".foldLeft(Map.empty[Int, (Int, Int)]) { (freqMap, wrs) =>
        val count = wrs.int("count")
        freqMap.applyOrElseAndUpdate(wrs.int("field"), (0, 0)) { case ((uvsNom, uvsNum)) =>
          if (wrs.boolean("nullnum")) (uvsNom + count, uvsNum) else (uvsNom + count, uvsNum + count)
        }
      }
      mysqlDBConnector.DBConn autoCommit { implicit session =>
        for ((fieldId, (uvsNom, uvsNum)) <- freqMap) {
          sql"UPDATE ${MysqlTables.FieldTable.table} SET ${MysqlTables.FieldTable.column.uniqueValuesSizeNominal} = $uvsNom, ${MysqlTables.FieldTable.column.uniqueValuesSizeNumeric} = $uvsNum WHERE ${MysqlTables.FieldTable.column.id} = $fieldId".execute().apply()
        }
      }
    }

    def clean(): Unit = for (filePath <- hdfs.listFiles if filePath.getName.startsWith(fileNamePrefix)) {
      hdfs.deleteFile(filePath.getName)
    }

  }

  def build(f: (FieldBuilder) => DataSourceDetail): DataSourceDetail = DataHdfs { implicit hdfs =>
    //HDFS initialization
    //Insert basic information about data source to the mysql
    taskStatusProcessor.newStatus("Meta information about a data source are creating...")
    val dataSourceId = mysqlDBConnector.DBConn autoCommit { implicit session =>
      sql"INSERT INTO ${MysqlTables.DataSourceTable.table} (${MysqlTables.DataSourceTable.column.name}, ${MysqlTables.DataSourceTable.column.`type`}, ${MysqlTables.DataSourceTable.column.size}) VALUES ($name, 'UNLIMITED', 0)".updateAndReturnGeneratedKey().apply().toInt
    }
    //create file name and file path within HDFS
    implicit val dataTable = new InstanceDataTable(hiveDBConnector.dbSettings.dbName + "-" + dataSourceId, ".csv")
    try {
      taskStatusProcessor.newStatus("Data source fields are creating...")
      //create hdfs csv writer and hive field builder for the particular data source
      val dataSourceDetail = f(new HiveFieldBuilder(DataSourceDetail(dataSourceId, name, UnlimitedDataSourceType, 0, false)))
      //active data source
      mysqlDBConnector.DBConn autoCommit { implicit session =>
        sql"UPDATE ${MysqlTables.DataSourceTable.table} SET ${MysqlTables.DataSourceTable.column.active} = true WHERE ${MysqlTables.DataSourceTable.column.id} = ${dataSourceDetail.id}".execute().apply()
      }
      //return created data source
      dataSourceDetail.copy(active = true)
    } catch {
      case ex: Throwable =>
        //if some exception during uploading then rollback all operation (it includes deleting all created hive and mysql tables)
        HiveDataSourceOps().deleteDataSource(dataSourceId)
        if (!ex.isInstanceOf[DataHdfs.Exceptions.FileExists]) {
          dataTable.clean()
        }
        throw ex
    }
  }

}

object HiveDataSourceBuilder {

  def apply(name: String)(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector, taskStatusProcessor: TaskStatusProcessor) = new ValidationDataSourceBuilder(new HiveDataSourceBuilder(name))

}
