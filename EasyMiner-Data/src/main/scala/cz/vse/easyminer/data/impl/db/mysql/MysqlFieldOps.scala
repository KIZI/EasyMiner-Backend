package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.ValidationFieldOps
import cz.vse.easyminer.data.impl.db.mysql.Tables.{FieldTable, InstanceTable}
import scalikejdbc._

/**
 * Created by propan on 20. 8. 2015.
 */
class MysqlFieldOps private[db](val dataSource: DataSourceDetail)(implicit connector: MysqlDBConnector) extends FieldOps {

  import connector._

  def renameField(fieldId: Int, newName: String): Unit = DBConn autoCommit { implicit session =>
    sql"UPDATE ${FieldTable.table} SET ${FieldTable.column.name} = $newName WHERE ${FieldTable.column.dataSource} = ${dataSource.id} AND ${FieldTable.column.id} = $fieldId".execute().apply()
  }

  def deleteField(fieldId: Int): Unit = DBConn autoCommit { implicit session =>
    sql"DELETE FROM ${FieldTable.table} WHERE ${FieldTable.column.dataSource} = ${dataSource.id} AND ${FieldTable.column.id} = $fieldId".execute().apply()
    val instanceTable = new InstanceTable(dataSource.id, List(fieldId))
    val column = instanceTable.columnById(fieldId)
    sql"SHOW TABLES LIKE ${instanceTable.tableName}".map(_ => true).first().apply match {
      case Some(true) => sql"ALTER TABLE ${instanceTable.table} DROP COLUMN $column".execute().apply()
      case _ =>
    }
  }

  def getAllFields: List[FieldDetail] = DBConn readOnly { implicit session =>
    val a = FieldTable.syntax("a")
    sql"SELECT ${a.result.*} FROM ${FieldTable as a} WHERE ${a.dataSource} = ${dataSource.id}".map(FieldTable(a.resultName)).list().apply()
  }

  def getField(fieldId: Int): Option[FieldDetail] = DBConn readOnly { implicit session =>
    val a = FieldTable.syntax("a")
    sql"SELECT ${a.result.*} FROM ${FieldTable as a} WHERE ${a.dataSource} = ${dataSource.id} AND ${a.id} = $fieldId".map(FieldTable(a.resultName)).first().apply()
  }

}

object MysqlFieldOps {

  def apply(dataSource: DataSourceDetail)(implicit mysqlDBConnector: MysqlDBConnector) = new ValidationFieldOps(new MysqlFieldOps(dataSource))

}
