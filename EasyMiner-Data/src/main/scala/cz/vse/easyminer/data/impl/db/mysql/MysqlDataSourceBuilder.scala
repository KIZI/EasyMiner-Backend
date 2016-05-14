package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.{DbFieldBuilder, ValidationDataSourceBuilder}
import cz.vse.easyminer.data.impl.db.mysql.Tables._
import scalikejdbc._

/**
 * Created by propan on 8. 8. 2015.
 */
class MysqlDataSourceBuilder private[db](val name: String)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) extends DataSourceBuilder {

  import mysqlDBConnector._

  val batchLimit = 100

  class MysqlValueBuilder(val dataSource: DataSourceDetail,
                          val fields: Seq[FieldDetail],
                          preparedStatement: String,
                          batchParameters: Vector[Seq[Any]]) extends ValueBuilder {

    def this(dataSource: DataSourceDetail, fields: Seq[FieldDetail]) = this(
      dataSource,
      fields,
      s"INSERT INTO `${Tables.tablePrefix}data_source_${dataSource.id}` (${fields.map(InstanceTable.colNamePrefix + _.id).mkString(", ")}) VALUES (${List.fill(fields.size)("?").mkString(", ")})",
      Vector()
    )

    private def flushBatch(): Unit = DBConn localTx { implicit session =>
      SQL(preparedStatement).batch(batchParameters: _*).apply()
      taskStatusProcessor.newStatus("The data source is now populating by uploaded instances... Inserted rows: " + dataSource.size)
    }

    def addInstance(values: Seq[Value]): ValueBuilder = if (batchParameters.size >= batchLimit) {
      flushBatch()
      new MysqlValueBuilder(
        dataSource,
        fields,
        preparedStatement,
        Vector()
      ).addInstance(values)
    } else {
      new MysqlValueBuilder(
        dataSource.copy(size = dataSource.size + 1),
        fields,
        preparedStatement,
        batchParameters :+ values.map {
          case NominalValue(value) => value
          case NumericValue(value) => value
          case NullValue => null
        }
      )
    }

    def build: DataSourceDetail = {
      if (batchParameters.nonEmpty) {
        flushBatch()
      }
      taskStatusProcessor.newStatus("Aggregated values and stats are now creating...")
      DBConn autoCommit { implicit session =>
        sql"UPDATE ${DataSourceTable.table} SET ${DataSourceTable.column.size} = ${dataSource.size} WHERE ${DataSourceTable.column.id} = ${dataSource.id}".execute().apply()
        val dataTable = new InstanceTable(dataSource.id, fields.map(_.id))
        val valueTable = new ValueTable(dataSource.id)
        for (field <- fields) {
          val dataCol = dataTable.columnById(field.id)
          val uniqueValuesSize = sql"SELECT COUNT(*) AS count FROM (SELECT $dataCol FROM ${dataTable.table} WHERE 1 GROUP BY $dataCol) c".map(_.int("count")).first().apply().getOrElse(0)
          val valueCol = field.`type` match {
            case NominalFieldType => valueTable.column.c("value_nominal")
            case NumericFieldType =>
              sql"""
              INSERT INTO ${FieldNumericDetailTable.table} (${FieldNumericDetailTable.column.columns})
              SELECT ${field.id} AS ${FieldNumericDetailTable.column.id},
              MIN($dataCol) AS ${FieldNumericDetailTable.column.min},
              MAX($dataCol) AS ${FieldNumericDetailTable.column.max},
              AVG($dataCol) AS ${FieldNumericDetailTable.column.avg}
              FROM ${dataTable.table}
              """.execute().apply()
              valueTable.column.c("value_numeric")
          }
          sql"UPDATE ${FieldTable.table} SET ${FieldTable.column.uniqueValuesSize} = $uniqueValuesSize WHERE ${FieldTable.column.id} = ${field.id}".execute().apply()
          sql"""
          INSERT INTO ${valueTable.table} (${valueTable.column.field("field")}, $valueCol, ${valueTable.column.frequency})
          SELECT ${field.id}, $dataCol, COUNT(*) FROM ${dataTable.table} GROUP BY $dataCol
          """.execute().apply()
        }
      }
      dataSource
    }

  }

  class MysqlFieldBuilder(val dataSource: DataSourceDetail, val fields: Vector[Field] = Vector()) extends DbFieldBuilder {

    val connector: MysqlDBConnector = implicitly[MysqlDBConnector]

    private def buildDataSourceTable(fieldsDetail: List[FieldDetail]) = {
      val instanceTable = new InstanceTable(dataSource.id, fieldsDetail.map(_.id))
      val valueTable = new ValueTable(dataSource.id)
      val sqlCols = fieldsDetail.map {
        case FieldDetail(id, _, _, NominalFieldType, _) => sqls"${instanceTable.columnById(id)} varchar(255) DEFAULT NULL"
        case FieldDetail(id, _, _, NumericFieldType, _) => sqls"${instanceTable.columnById(id)} double DEFAULT NULL"
      }
      DBConn autoCommit { implicit session =>
        sql"CREATE TABLE ${instanceTable.table} (${instanceTable.column.id} int(10) unsigned NOT NULL AUTO_INCREMENT, PRIMARY KEY (${instanceTable.column.id}), $sqlCols) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1".execute().apply()
        sql"""CREATE TABLE ${valueTable.table} (
        ${valueTable.column.id} int(10) unsigned NOT NULL AUTO_INCREMENT,
        ${valueTable.column.field("field")} int(10) unsigned NOT NULL,
        ${valueTable.column.valueNominal} varchar(255) DEFAULT NULL,
        ${valueTable.column.valueNumeric} double DEFAULT NULL,
        ${valueTable.column.frequency} int(10) unsigned NOT NULL,
        PRIMARY KEY (${valueTable.column.id}, ${valueTable.column.field("field")}),
        KEY ${valueTable.column.field("field")} (${valueTable.column.field("field")})
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1""".execute().apply()
        sql"""ALTER TABLE ${valueTable.table}
        ADD CONSTRAINT ${valueTable.table}_ibfk_1
        FOREIGN KEY (${valueTable.column.field("field")})
        REFERENCES ${FieldTable.table} (${FieldTable.column.id})
        ON DELETE CASCADE ON UPDATE CASCADE""".execute().apply()
      }
    }

    def field(field: Field): FieldBuilder = new MysqlFieldBuilder(dataSource, fields :+ field)

    def build(f: (ValueBuilder) => DataSourceDetail): DataSourceDetail = {
      val fieldsDetail = buildFields
      buildDataSourceTable(fieldsDetail)
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