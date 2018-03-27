/**
  * This file inherits implicits from two package objects: easyminer (actorSystem), db (lock table)
  */
package cz.vse.easyminer
package preprocessing.impl.db
package hive

import cz.vse.easyminer.core.PersistentLock
import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.preprocessing.impl.PersistentLocks
import cz.vse.easyminer.preprocessing.impl.db.hive.Tables.{InstanceTable, ValueTable}
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlAttributeOps
import cz.vse.easyminer.preprocessing.{AttributeDetail, AttributeOps, DatasetDetail}
import scalikejdbc._

/**
  * Created by propan on 27. 1. 2016.
  */
class HiveAttributeOps private(mysqlAttributeOps: MysqlAttributeOps)(implicit connector: HiveDBConnector, mysqlDBConnector: MysqlDBConnector) extends AttributeOps {

  import connector._

  val dataset: DatasetDetail = mysqlAttributeOps.dataset

  def renameAttribute(attributeId: Int, newName: String): Unit = mysqlAttributeOps.renameAttribute(attributeId, newName)

  def getAttribute(attributeId: Int): Option[AttributeDetail] = mysqlAttributeOps.getAttribute(attributeId)

  def deleteAttribute(attributeId: Int): Unit = PersistentLock(PersistentLocks.datasetLockName(dataset.id)) {
    DBConn autoCommit { implicit session =>
      val valueTable = new ValueTable(dataset.id)
      val instanceTable = new InstanceTable(dataset.id)
      sql"ALTER TABLE ${instanceTable.table} DROP IF EXISTS PARTITION (${instanceTable.column.attribute} = $attributeId) PURGE".execute().apply()
      sql"ALTER TABLE ${valueTable.table} DROP IF EXISTS PARTITION (${valueTable.column.attribute} = $attributeId) PURGE".execute().apply()
    }
    mysqlAttributeOps.deleteAttribute(attributeId)
  }

  def getAllAttributes: List[AttributeDetail] = mysqlAttributeOps.getAllAttributes

}

object HiveAttributeOps {

  import DatasetTypeConversions.Unlimited._

  def apply(datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector): AttributeOps = new ValidationAttributeOps(
    new HiveAttributeOps(new MysqlAttributeOps(datasetDetail))
  )

}