/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.preprocessing.impl.db.ValidationDatasetOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{ValueTable, InstanceTable, DatasetTable}
import cz.vse.easyminer.preprocessing.{DatasetDetail, DatasetOps}
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 22. 12. 2015.
  */

/**
  * All operations for existed datasets of a specific user connection
  *
  * @param mysqlDBConnector mysql database connection
  */
class MysqlDatasetOps private[db](implicit mysqlDBConnector: MysqlDBConnector) extends DatasetOps {

  import mysqlDBConnector._

  /**
    * Rename dataset
    *
    * @param datasetId dataset id
    * @param newName   new name
    */
  def renameDataset(datasetId: Int, newName: String): Unit = DBConn autoCommit { implicit session =>
    sql"UPDATE ${DatasetTable.table} SET ${DatasetTable.column.name} = $newName WHERE ${DatasetTable.column.id} = $datasetId".execute().apply()
  }

  /**
    * Get dataset by ID
    *
    * @param datasetId dataset ID
    * @return dataset detail or None if dataset does not exist
    */
  def getDataset(datasetId: Int): Option[DatasetDetail] = DBConn readOnly { implicit session =>
    val d = DatasetTable.syntax("d")
    sql"SELECT ${d.result.*} FROM ${DatasetTable as d} WHERE ${d.id} = $datasetId AND ${d.active} = 1".map(DatasetTable(d.resultName)).first().apply()
  }

  /**
    * Delete dataset
    *
    * @param datasetId dataset id
    */
  def deleteDataset(datasetId: Int): Unit = DBConn localTx { implicit session =>
    val instanceTable = new InstanceTable(datasetId)
    val valueTable = new ValueTable(datasetId)
    sql"DELETE FROM ${DatasetTable.table} WHERE ${DatasetTable.column.id} = $datasetId".execute().apply()
    sql"DROP TABLE IF EXISTS ${instanceTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${valueTable.table}".execute().apply()
  }

  def getDataset(datasetId: Int): Option[DatasetDetail] = DBConn readOnly { implicit session =>
    val d = DatasetTable.syntax("d")
    sql"SELECT ${d.result.*} FROM ${DatasetTable as d} WHERE ${d.id} = $datasetId AND ${d.active} = 1".map(DatasetTable(d.resultName)).first().apply()
  }

  /**
    * Get all existed dataset
    *
    * @return list of datasets
    */
  def getAllDatasets: List[DatasetDetail] = DBConn readOnly { implicit session =>
    val d = DatasetTable.syntax("d")
    sql"SELECT ${d.result.*} FROM ${DatasetTable as d} WHERE ${d.active} = 1".map(DatasetTable(d.resultName)).list().apply()
  }

  /**
    * It only changes updated timestamp
    *
    * @param datasetId ID
    */
  def updateDataset(datasetId: Int): Unit = DBConn autoCommit { implicit session =>
    sql"UPDATE ${DatasetTable.table} SET ${DatasetTable.column.updated} = CURRENT_TIMESTAMP() WHERE ${DatasetTable.column.id} = $datasetId".execute().apply()
  }

}

object MysqlDatasetOps {

  /**
    * Create dataset operations instance decorated by validator
    *
    * @param mysqlDBConnector mysql database connection
    * @return dataset operations instance
    */
  def apply()(implicit mysqlDBConnector: MysqlDBConnector): DatasetOps = new ValidationDatasetOps(new MysqlDatasetOps())

}