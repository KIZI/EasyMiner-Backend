package cz.vse.easyminer.data.impl.db.mysql

import java.io.PrintWriter

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.{DbFieldBuilder, ValidationDataSourceBuilder}
import cz.vse.easyminer.data.impl.db.mysql.Tables._
import scalikejdbc._
import cz.vse.easyminer.core.util.MapOps.PimpedMap

/**
  * Created by propan on 8. 8. 2015.
  */
class MysqlDataSourceBuilder private[db](val name: String)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) extends DataSourceBuilder {

  import mysqlDBConnector._

  val batchLimit = 100

  class MysqlValueBuilder(val dataSource: DataSourceDetail,
                          val fields: Seq[FieldDetail],
                          fieldMap: Map[Int, FieldDetail],
                          preparedStatement: String,
                          batchParameters: Vector[Seq[Any]]) extends ValueBuilder {

    def this(dataSource: DataSourceDetail, fields: Seq[FieldDetail]) = this(
      dataSource,
      fields,
      fields.iterator.map(fieldDetail => fieldDetail.id -> fieldDetail).toMap, {
        val instanceTable = new InstanceTable(dataSource.id)
        sqls"INSERT INTO `${instanceTable.table}` (${instanceTable.column.id}, ${instanceTable.column.field("field")}, ${instanceTable.column.valueNominal}, ${instanceTable.column.valueNumeric}) VALUES (?, ?, ?, ?)".value
      },
      Vector()
    )

    private def flushBatch(): Unit = DBConn localTx { implicit session =>
      SQL(preparedStatement).batch(batchParameters: _*).apply()
      taskStatusProcessor.newStatus("The data source is now populating by uploaded instances... Inserted rows: " + dataSource.size)
    }

    def addTransaction(itemset: Set[(FieldDetail, Value)]): ValueBuilder = if (batchParameters.size >= batchLimit) {
      flushBatch()
      new MysqlValueBuilder(
        dataSource,
        fields,
        fieldMap,
        preparedStatement,
        Vector()
      ).addTransaction(itemset)
    } else {
      new MysqlValueBuilder(
        dataSource.copy(size = dataSource.size + 1),
        fields,
        itemset.foldLeft(fieldMap) {
          case (fieldMap, (fieldDetail, _: NumericValue)) => fieldMap.applyAndUpdate(fieldDetail.id)(fieldDetail => fieldDetail.copy(supportNominal = fieldDetail.supportNominal + 1, supportNumeric = fieldDetail.supportNumeric + 1))
          case (fieldMap, (fieldDetail, _: NominalValue)) => fieldMap.applyAndUpdate(fieldDetail.id)(fieldDetail => fieldDetail.copy(supportNominal = fieldDetail.supportNominal + 1))
          case (fieldMap, _) => fieldMap
        },
        preparedStatement,
        batchParameters ++ itemset.collect {
          case (fieldDetail, NominalValue(value)) => List(dataSource.size + 1, fieldDetail.id, value, null)
          case (fieldDetail, NumericValue(original, value)) => List(dataSource.size + 1, fieldDetail.id, original, value)
        }
      )
    }

    def build: DataSourceDetail = {
      if (batchParameters.nonEmpty) {
        flushBatch()
      }
      taskStatusProcessor.newStatus("Aggregated values and stats are now creating...")
      DBConn autoCommit { implicit session =>
        val instanceTable = new InstanceTable(dataSource.id)
        val valueTable = new ValueTable(dataSource.id)
        //update datasource size
        sql"UPDATE ${DataSourceTable.table} SET ${DataSourceTable.column.size} = ${dataSource.size} WHERE ${DataSourceTable.column.id} = ${dataSource.id}".execute().apply()
        //insert histogram for any field
        sql"""
        INSERT INTO ${valueTable.table} (${valueTable.column.field("field")}, ${valueTable.column.valueNominal}, ${valueTable.column.valueNumeric}, ${valueTable.column.frequency})
        SELECT ${instanceTable.column.field("field")}, ${instanceTable.column.valueNominal}, ${instanceTable.column.valueNumeric}, COUNT(*)
        FROM ${instanceTable.table}
        GROUP BY ${instanceTable.column.field("field")}, ${instanceTable.column.valueNumeric}, ${instanceTable.column.valueNominal}
        """.execute().apply()
        //update unique values size for each field
        //update support for each field
        for (fieldDetail <- fieldMap.values) {
          sql"""
          UPDATE ${FieldTable.table}
          SET ${FieldTable.column.field("supportNominal")} = ${fieldDetail.supportNominal},
          ${FieldTable.column.field("supportNumeric")} = ${fieldDetail.supportNumeric},
          ${FieldTable.column.uniqueValuesSizeNominal} = (SELECT COUNT(*) FROM ${valueTable.table} WHERE ${valueTable.column.field("field")} = ${fieldDetail.id}),
          ${FieldTable.column.uniqueValuesSizeNumeric} = (SELECT COUNT(*) FROM ${valueTable.table} WHERE ${valueTable.column.field("field")} = ${fieldDetail.id} AND ${valueTable.column.valueNumeric} IS NOT NULL)
          WHERE ${FieldTable.column.id} = ${fieldDetail.id}
          """.execute().apply()
        }
        //insert statistics for numeric fields
        val ft = FieldTable.syntax("ft")
        val it = instanceTable.syntax("it")
        sql"""
        INSERT INTO ${FieldNumericDetailTable.table} (${FieldNumericDetailTable.column.columns})
        SELECT ${ft.id},
        MIN(${it.valueNumeric}),
        MAX(${it.valueNumeric}),
        AVG(${it.valueNumeric})
        FROM ${FieldTable as ft} JOIN ${instanceTable as it} ON (${ft.id} = ${it.field("field")})
        WHERE ${ft.dataSource} = ${dataSource.id}
        GROUP BY ${ft.id} HAVING MIN(${it.valueNumeric}) IS NOT NULL
        """.execute().apply()
      }
      dataSource
    }

  }

  class MysqlFieldBuilder(val dataSource: DataSourceDetail, val fields: Vector[Field] = Vector()) extends DbFieldBuilder {

    val connector: MysqlDBConnector = implicitly[MysqlDBConnector]

    private def buildDataSourceTable = {
      val instanceTable = new InstanceTable(dataSource.id)
      val valueTable = new ValueTable(dataSource.id)
      DBConn autoCommit { implicit session =>
        //instance table
        sql"""CREATE TABLE ${instanceTable.table} (
        pid int(10) unsigned NOT NULL AUTO_INCREMENT,
        ${instanceTable.column.id} int(10) unsigned NOT NULL,
        ${instanceTable.column.field("field")} int(10) unsigned NOT NULL,
        ${instanceTable.column.valueNominal} varchar(255) DEFAULT NULL,
        ${instanceTable.column.valueNumeric} double DEFAULT NULL,
        KEY ${instanceTable.column.field("field")} (${instanceTable.column.field("field")}),
        KEY k_id_field (${instanceTable.column.id}, ${instanceTable.column.field("field")}),
        PRIMARY KEY (pid)
        ) ENGINE=MYISAM DEFAULT CHARSET=utf8 COLLATE=utf8_bin""".execute().apply()
        //value table
        sql"""CREATE TABLE ${valueTable.table} (
        ${valueTable.column.id} int(10) unsigned NOT NULL AUTO_INCREMENT,
        ${valueTable.column.field("field")} int(10) unsigned NOT NULL,
        ${valueTable.column.valueNominal} varchar(255) DEFAULT NULL,
        ${valueTable.column.valueNumeric} double DEFAULT NULL,
        ${valueTable.column.frequency} int(10) unsigned NOT NULL,
        PRIMARY KEY (${valueTable.column.id}, ${valueTable.column.field("field")}),
        KEY ${valueTable.column.field("field")} (${valueTable.column.field("field")})
        ) ENGINE=MYISAM DEFAULT CHARSET=utf8 COLLATE=utf8_bin AUTO_INCREMENT=1""".execute().apply()
      }
    }

    def field(field: Field): FieldBuilder = new MysqlFieldBuilder(dataSource, fields :+ field)

    def build(f: (ValueBuilder) => DataSourceDetail): DataSourceDetail = {
      val fieldsDetail = buildFields
      buildDataSourceTable
      taskStatusProcessor.newStatus("The data source is now populating by uploaded instances...")
      f(new MysqlValueBuilder(dataSource, fieldsDetail))
    }

  }

  def build(f: (FieldBuilder) => DataSourceDetail): DataSourceDetail = {
    taskStatusProcessor.newStatus("Meta information about a data source are creating...")
    val dataSourceId = DBConn autoCommit { implicit session =>
      sql"INSERT INTO ${DataSourceTable.table} (${DataSourceTable.column.name}, ${DataSourceTable.column.size}) VALUES ($name, 0)".updateAndReturnGeneratedKey().apply().toInt
    }
    try {
      taskStatusProcessor.newStatus("Data source fields are creating...")
      val dataSourceDetail = f(new MysqlFieldBuilder(DataSourceDetail(dataSourceId, name, LimitedDataSourceType, 0, false)))
      DBConn autoCommit { implicit session =>
        sql"UPDATE ${DataSourceTable.table} SET ${DataSourceTable.column.active} = true WHERE ${DataSourceTable.column.id} = ${dataSourceDetail.id}".execute().apply()
      }
      dataSourceDetail.copy(active = true)
    } catch {
      case ex: Throwable =>
        DBConn autoCommit { implicit session =>
          sql"DELETE FROM ${DataSourceTable.table} WHERE ${DataSourceTable.column.id} = $dataSourceId".execute().apply()
          val dataTable = new InstanceTable(dataSourceId)
          sql"DROP TABLE IF EXISTS ${dataTable.table}".execute().apply()
          val valueTable = new ValueTable(dataSourceId)
          sql"DROP TABLE IF EXISTS ${valueTable.table}".execute().apply()
        }
        throw ex
    }
  }

}

object MysqlDataSourceBuilder {

  def apply(name: String)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) = new ValidationDataSourceBuilder(new MysqlDataSourceBuilder(name))

}