/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.mysql.Tables.{DataSourceTable, InstanceTable, ValueTable}
import cz.vse.easyminer.data.impl.db.{DbDataSourceOps, ValidationDataSourceOps}
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 16. 8. 2015.
  */

/**
  * Implementation of data source operations on mysql database
  *
  * @param mysqlDBConnector implicit! mysql db connector
  */
class MysqlDataSourceOps private[db](implicit private[db] val mysqlDBConnector: MysqlDBConnector) extends DbDataSourceOps {

  import mysqlDBConnector._


  /**
    * Rename data source
    *
    * @param dataSourceId data source id
    * @param newName      new name
    */
  def renameDataSource(dataSourceId: Int, newName: String): Unit = DBConn autoCommit { implicit session =>
    sql"UPDATE ${DataSourceTable.table} SET ${DataSourceTable.column.name} = $newName WHERE ${DataSourceTable.column.id} = $dataSourceId".execute().apply()
  }

  /**
    * Get detail of a data source
    *
    * @param dataSourceId data source id
    * @return data source detail or None if there is no data source with the ID
    */
  def getDataSource(dataSourceId: Int): Option[DataSourceDetail] = DBConn readOnly { implicit session =>
    val d = DataSourceTable.syntax("d")
    sql"SELECT ${d.result.*} FROM ${DataSourceTable as d} WHERE ${d.id} = $dataSourceId AND ${d.active} = 1".map(DataSourceTable(d.resultName)).first().apply()
  }


  /**
    * Delete data source
    *
    * @param dataSourceId data source id
    */
  def deleteDataSource(dataSourceId: Int): Unit = DBConn localTx { implicit session =>
    val dataTable = new InstanceTable(dataSourceId)
    val valueTable = new ValueTable(dataSourceId)
    sql"DELETE FROM ${DataSourceTable.table} WHERE ${DataSourceTable.column.id} = $dataSourceId".execute().apply()
    sql"DROP TABLE IF EXISTS ${dataTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${valueTable.table}".execute().apply()
  }


  /**
    * Get all details of all existed data sources
    *
    * @return list of data sources
    */
  def getAllDataSources: List[DataSourceDetail] = DBConn readOnly { implicit session =>
    val d = DataSourceTable.syntax("d")
    sql"SELECT ${d.result.*} FROM ${DataSourceTable as d} WHERE ${d.active} = 1".map(DataSourceTable(d.resultName)).list().apply()
  }

  /**
    * Get instances (transactions) with form of couples for a data source
    *
    * @param dataSourceId data source id
    * @param fieldIds     list of projected fields, if empty it should return all columns
    * @param offset       start pointer. First record is 0 (not 1)
    * @param limit        number of couples. It is restricted by the maximal limit value
    * @return instances with fields
    */
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

  /**
    * Create data source operations instance decorated by validator
    *
    * @param mysqlDBConnector implicit! mysql connector
    * @return data source operations object
    */
  def apply()(implicit mysqlDBConnector: MysqlDBConnector) = new ValidationDataSourceOps(new MysqlDataSourceOps)

}