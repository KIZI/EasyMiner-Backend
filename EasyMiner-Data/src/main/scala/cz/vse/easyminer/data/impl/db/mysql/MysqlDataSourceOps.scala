package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.mysql.Tables.{DataSourceTable, InstanceTable, ValueTable}
import cz.vse.easyminer.data.impl.db.{DbDataSourceOps, ValidationDataSourceOps}
import scalikejdbc._

/**
  * Created by propan on 16. 8. 2015.
  */
class MysqlDataSourceOps private[db](implicit private[db] val mysqlDBConnector: MysqlDBConnector) extends DbDataSourceOps {

  import mysqlDBConnector._

  def renameDataSource(dataSourceId: Int, newName: String): Unit = DBConn autoCommit { implicit session =>
    sql"UPDATE ${DataSourceTable.table} SET ${DataSourceTable.column.name} = $newName WHERE ${DataSourceTable.column.id} = $dataSourceId".execute().apply()
  }

  def getDataSource(dataSourceId: Int): Option[DataSourceDetail] = DBConn readOnly { implicit session =>
    val d = DataSourceTable.syntax("d")
    sql"SELECT ${d.result.*} FROM ${DataSourceTable as d} WHERE ${d.id} = $dataSourceId AND ${d.active} = 1".map(DataSourceTable(d.resultName)).first().apply()
  }

  def deleteDataSource(dataSourceId: Int): Unit = DBConn localTx { implicit session =>
    val dataTable = new InstanceTable(dataSourceId)
    val valueTable = new ValueTable(dataSourceId)
    sql"DELETE FROM ${DataSourceTable.table} WHERE ${DataSourceTable.column.id} = $dataSourceId".execute().apply()
    sql"DROP TABLE IF EXISTS ${dataTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${valueTable.table}".execute().apply()
  }

  def getAllDataSources: List[DataSourceDetail] = DBConn readOnly { implicit session =>
    val d = DataSourceTable.syntax("d")
    sql"SELECT ${d.result.*} FROM ${DataSourceTable as d} WHERE ${d.active} = 1".map(DataSourceTable(d.resultName)).list().apply()
  }

  def getInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Seq[Instance] = getDataSource(dataSourceId).toList.flatMap { dataSource =>
    val instanceTable = new InstanceTable(dataSource.id)
    val d = instanceTable.syntax("d")
    val fieldMap = MysqlFieldOps(dataSource).getAllFields.filter(field => fieldIds.isEmpty || fieldIds.contains(field.id)).map(field => field.id -> field).toMap
    DBConn readOnly { implicit session =>
      implicit val fieldIdToFieldDetail = fieldMap.apply _
      sql"SELECT ${d.result.*} FROM ${instanceTable as d} WHERE ${d.field("field")} IN (${fieldMap.keys}) AND ${d.id} > $offset AND ${d.id} <= ($offset + $limit)".map(instanceTable(d.resultName)).list().apply().flatten
    }
  }

}

object MysqlDataSourceOps {

  def apply()(implicit mysqlDBConnector: MysqlDBConnector) = new ValidationDataSourceOps(new MysqlDataSourceOps)

}