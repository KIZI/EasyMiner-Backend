/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.ValidationFieldOps
import cz.vse.easyminer.data.impl.db.mysql.Tables.{FieldTable, InstanceTable, ValueTable}
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 20. 8. 2015.
  */

/**
  * Implementation of field operations on mysql database
  *
  * @param dataSource data source
  * @param connector  implicit! mysql db connector
  */
class MysqlFieldOps private[db](val dataSource: DataSourceDetail)(implicit connector: MysqlDBConnector) extends FieldOps {

  import connector._

  /**
    * Rename field name
    *
    * @param fieldId field id
    * @param newName new name
    */
  def renameField(fieldId: Int, newName: String): Unit = DBConn autoCommit { implicit session =>
    sql"UPDATE ${FieldTable.table} SET ${FieldTable.column.name} = $newName WHERE ${FieldTable.column.dataSource} = ${dataSource.id} AND ${FieldTable.column.id} = $fieldId".execute().apply()
  }

  /**
    * Delete field from the data source
    *
    * @param fieldId field id
    */
  def deleteField(fieldId: Int): Unit = DBConn autoCommit { implicit session =>
    val dataTable = new InstanceTable(dataSource.id)
    val valueTable = new ValueTable(dataSource.id)
    sql"DELETE FROM ${FieldTable.table} WHERE ${FieldTable.column.dataSource} = ${dataSource.id} AND ${FieldTable.column.id} = $fieldId".execute().apply()
    sql"SHOW TABLES LIKE ${valueTable.tableName}".map(_ => true).first().apply.foreach(_ => sql"DELETE FROM ${valueTable.table} WHERE ${valueTable.column.field("field")} = $fieldId".execute().apply())
    sql"SHOW TABLES LIKE ${dataTable.tableName}".map(_ => true).first().apply.foreach(_ => sql"DELETE FROM ${dataTable.table} WHERE ${dataTable.column.field("field")} = $fieldId".execute().apply())
  }

  /**
    * Get all fields within the data source
    *
    * @return list of fields
    */
  def getAllFields: List[FieldDetail] = DBConn readOnly { implicit session =>
    val a = FieldTable.syntax("a")
    sql"SELECT ${a.result.*} FROM ${FieldTable as a} WHERE ${a.dataSource} = ${dataSource.id}".map(FieldTable(a.resultName)).list().apply()
  }

  /**
    * Get field detail by field id
    *
    * @param fieldId field id
    * @return field detail or None if there is no data field with the ID
    */
  def getField(fieldId: Int): Option[FieldDetail] = DBConn readOnly { implicit session =>
    val a = FieldTable.syntax("a")
    sql"SELECT ${a.result.*} FROM ${FieldTable as a} WHERE ${a.dataSource} = ${dataSource.id} AND ${a.id} = $fieldId".map(FieldTable(a.resultName)).first().apply()
  }

  /**
    * Change type of a field (nominal -> numeric, numeric -> nominal)
    *
    * @param fieldId field id
    * @return the change was successful
    */
  def changeFieldType(fieldId: Int): Boolean = getField(fieldId)
    .filter(fieldDetail => fieldDetail.`type` == NumericFieldType || fieldDetail.uniqueValuesSizeNumeric > 0)
    .exists { fieldDetail =>
      DBConn autoCommit { implicit session =>
        val newType = if (fieldDetail.`type` == NumericFieldType) FieldTable.nominalName else FieldTable.numericName
        sql"UPDATE ${FieldTable.table} SET ${FieldTable.column.`type`} = $newType WHERE ${FieldTable.column.dataSource} = ${dataSource.id} AND ${FieldTable.column.id} = $fieldId".execute().apply()
      }
      true
    }

}

object MysqlFieldOps {

  /**
    * Create field operations instance decorated by validator
    *
    * @param dataSource       data source
    * @param mysqlDBConnector implicit! mysql db connector
    * @return field operations instance
    */
  def apply(dataSource: DataSourceDetail)(implicit mysqlDBConnector: MysqlDBConnector) = new ValidationFieldOps(new MysqlFieldOps(dataSource))

}
