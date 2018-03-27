package cz.vse.easyminer.data.impl.db.hive

import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.hive.Tables.{InstanceTable, ValueTable}
import cz.vse.easyminer.data.impl.db.mysql.MysqlDataSourceOps
import cz.vse.easyminer.data.impl.db.{DbDataSourceOps, ValidationDataSourceOps}
import scalikejdbc._

/**
  * Created by propan on 8. 12. 2015.
  */
class HiveDataSourceOps private[db](mysqlDataSourceOps: MysqlDataSourceOps)(implicit private[db] val mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector) extends DbDataSourceOps {

  import hiveDBConnector._

  def renameDataSource(dataSourceId: Int, newName: String): Unit = mysqlDataSourceOps.renameDataSource(dataSourceId, newName)

  def deleteDataSource(dataSourceId: Int): Unit = {
    DBConn autoCommit { implicit session =>
      val instanceTable = new InstanceTable(dataSourceId)
      val valueTable = new ValueTable(dataSourceId)
      sql"DROP TABLE IF EXISTS ${valueTable.table} PURGE".execute().apply()
      sql"DROP TABLE IF EXISTS ${instanceTable.table} PURGE".execute().apply()
    }
    mysqlDataSourceOps.deleteDataSource(dataSourceId)
  }

  def getAllDataSources: List[DataSourceDetail] = mysqlDataSourceOps.getAllDataSources

  def getDataSource(dataSourceId: Int): Option[DataSourceDetail] = mysqlDataSourceOps.getDataSource(dataSourceId)

  def getInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Seq[Instance] = getDataSource(dataSourceId).toList.flatMap { dataSource =>
    val instanceTable = new InstanceTable(dataSource.id)
    val d = instanceTable.syntax("d")
    val fieldMap = HiveFieldOps(dataSource).getAllFields.filter(field => fieldIds.isEmpty || fieldIds.contains(field.id)).map(field => field.id -> field).toMap
    DBConn readOnly { implicit session =>
      implicit val fieldIdToFieldDetail = fieldMap.apply _
      sql"SELECT ${d.result.*} FROM ${instanceTable as d} WHERE ${d.field("field")} IN (${fieldMap.keys}) AND ${d.id} > $offset AND ${d.id} <= ($offset + $limit)".map(instanceTable(d.resultName)).list().apply().flatten
    }
  }

}

object HiveDataSourceOps {

  def apply()(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector) = new ValidationDataSourceOps(new HiveDataSourceOps(new MysqlDataSourceOps))

}