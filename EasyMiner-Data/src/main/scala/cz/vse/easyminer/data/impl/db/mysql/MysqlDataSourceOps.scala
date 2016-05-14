package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.{ValidationDataSourceOps, DbDataSourceOps}
import cz.vse.easyminer.data.impl.db.mysql.Tables.{ValueTable, DataSourceTable, InstanceTable}
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

  private[db] def fetchInstances(dataSourceId: Int, fields: Seq[FieldDetail], offset: Int, limit: Int): Instances = {
    val instanceTable = new InstanceTable(dataSourceId, fields.map(_.id))
    val d = instanceTable.syntax("d")
    val instances = DBConn readOnly { implicit session =>
      sql"SELECT ${d.result.*} FROM ${instanceTable as d} LIMIT $limit OFFSET $offset".map(instanceTable(d.resultName, fields)).list().apply()
    }
    Instances(fields, instances)
  }

}

object MysqlDataSourceOps {

  def apply()(implicit mysqlDBConnector: MysqlDBConnector) = new ValidationDataSourceOps(new MysqlDataSourceOps)

}