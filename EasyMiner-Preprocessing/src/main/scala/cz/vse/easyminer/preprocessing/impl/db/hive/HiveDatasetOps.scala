/**
  * This file inherits implicits from two package objects: easyminer (actorSystem), db (lock table)
  */
package cz.vse.easyminer
package preprocessing.impl.db
package hive

import cz.vse.easyminer.core.PersistentLock
import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.preprocessing.{DatasetDetail, DatasetOps}
import cz.vse.easyminer.preprocessing.impl.PersistentLocks
import cz.vse.easyminer.preprocessing.impl.db.hive.Tables.{InstanceTable, ValueTable}
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlDatasetOps
import scalikejdbc._

/**
  * Created by propan on 22. 12. 2015.
  */
class HiveDatasetOps private(mysqlDatasetOps: MysqlDatasetOps)(implicit hiveDBConnector: HiveDBConnector, mysqlDBConnector: MysqlDBConnector) extends DatasetOps {

  import hiveDBConnector._

  def renameDataset(datasetId: Int, newName: String): Unit = mysqlDatasetOps.renameDataset(datasetId, newName)

  def deleteDataset(datasetId: Int): Unit = PersistentLock(PersistentLocks.datasetLockName(datasetId)) {
    DBConn autoCommit { implicit session =>
      val instanceTable = new InstanceTable(datasetId)
      val valueTable = new ValueTable(datasetId)
      sql"DROP TABLE IF EXISTS ${valueTable.table} PURGE".execute().apply()
      sql"DROP TABLE IF EXISTS ${instanceTable.table} PURGE".execute().apply()
    }
    mysqlDatasetOps.deleteDataset(datasetId)
  }

  def getDataset(datasetId: Int): Option[DatasetDetail] = mysqlDatasetOps.getDataset(datasetId)

  def getAllDatasets: List[DatasetDetail] = mysqlDatasetOps.getAllDatasets

  /**
    * It only changes updated timestamp
    *
    * @param datasetId ID
    */
  def updateDataset(datasetId: Int): Unit = mysqlDatasetOps.updateDataset(datasetId)

}

object HiveDatasetOps {

  def apply()(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector): DatasetOps = new ValidationDatasetOps(new HiveDatasetOps(new MysqlDatasetOps()))

}