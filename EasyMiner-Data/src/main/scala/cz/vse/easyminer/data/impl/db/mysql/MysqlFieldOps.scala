package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.ValidationFieldOps
import cz.vse.easyminer.data.impl.db.mysql.Tables.{FieldTable, InstanceTable, ValueTable}
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
    val dataTable = new InstanceTable(dataSource.id)
    val valueTable = new ValueTable(dataSource.id)
    sql"DELETE FROM ${FieldTable.table} WHERE ${FieldTable.column.dataSource} = ${dataSource.id} AND ${FieldTable.column.id} = $fieldId".execute().apply()
    sql"DELETE FROM ${valueTable.table} WHERE ${valueTable.column.field("field")} = $fieldId".execute().apply()
    sql"DELETE FROM ${dataTable.table} WHERE ${dataTable.column.field("field")} = $fieldId".execute().apply()
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
