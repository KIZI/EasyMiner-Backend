package cz.vse.easyminer.data.impl.db.hive

import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.data.impl.db.ValidationFieldOps
import cz.vse.easyminer.data.impl.db.hive.Tables.{InstanceTable, ValueTable}
import cz.vse.easyminer.data.impl.db.mysql.MysqlFieldOps
import cz.vse.easyminer.data.{DataSourceDetail, FieldDetail, FieldOps}
import scalikejdbc._

/**
 * Created by propan on 8. 12. 2015.
 */
class HiveFieldOps private[db](mysqlFieldOps: MysqlFieldOps)(implicit hiveDBConnector: HiveDBConnector) extends FieldOps {

  import hiveDBConnector._

  val dataSource: DataSourceDetail = mysqlFieldOps.dataSource

  def renameField(fieldId: Int, newName: String): Unit = mysqlFieldOps.renameField(fieldId, newName)

  def getAllFields: List[FieldDetail] = mysqlFieldOps.getAllFields

  def deleteField(fieldId: Int): Unit = {
    DBConn autoCommit { implicit session =>
      val valueTable = new ValueTable(dataSource.id)
      val narrowInstanceTable = new InstanceTable(dataSource.id)
      sql"ALTER TABLE ${valueTable.table} DROP IF EXISTS PARTITION (${valueTable.column.field("field")} = $fieldId) PURGE".execute().apply()
      sql"ALTER TABLE ${narrowInstanceTable.table} DROP IF EXISTS PARTITION (${narrowInstanceTable.column.field("field")} = $fieldId) PURGE".execute().apply()
    }
    mysqlFieldOps.deleteField(fieldId)
  }

  def getField(fieldId: Int): Option[FieldDetail] = mysqlFieldOps.getField(fieldId)

  def changeFieldType(fieldId: Int): Boolean = mysqlFieldOps.changeFieldType(fieldId)

}

object HiveFieldOps {

  def apply(dataSourceDetail: DataSourceDetail)(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector) = new ValidationFieldOps(new HiveFieldOps(new MysqlFieldOps(dataSourceDetail)))

}