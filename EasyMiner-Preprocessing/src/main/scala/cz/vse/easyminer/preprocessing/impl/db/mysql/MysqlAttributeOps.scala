/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.preprocessing.impl.db.ValidationAttributeOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeTable, InstanceTable, ValueTable}
import cz.vse.easyminer.preprocessing.{AttributeDetail, AttributeOps, DatasetDetail}
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 27. 1. 2016.
  */

/**
  * All operations for existed data attributes within a dataset
  *
  * @param dataset   dataset detail
  * @param connector mysql database connection
  */
class MysqlAttributeOps private[db](val dataset: DatasetDetail)(implicit connector: MysqlDBConnector) extends AttributeOps {

  import connector._

  /**
    * Rename attribute
    *
    * @param attributeId attribute id
    * @param newName     new name
    */
  def renameAttribute(attributeId: Int, newName: String): Unit = DBConn autoCommit { implicit session =>
    sql"UPDATE ${AttributeTable.table} SET ${AttributeTable.column.name} = $newName WHERE ${AttributeTable.column.dataset} = ${dataset.id} AND ${AttributeTable.column.id} = $attributeId".execute().apply()
  }

  /**
    * Get attribute from an ID. If the id does not exist than return None
    *
    * @param attributeId attribute id
    * @return attribute detail or None if it does not exist
    */
  def getAttribute(attributeId: Int): Option[AttributeDetail] = DBConn readOnly { implicit session =>
    val a = AttributeTable.syntax("a")
    sql"SELECT ${a.result.*} FROM ${AttributeTable as a} WHERE ${a.dataset} = ${dataset.id} AND ${a.id} = $attributeId AND ${a.active} = 1".map(AttributeTable(a.resultName)).first().apply()
  }

  /**
    * Delete attribute
    *
    * @param attributeId attribute id
    */
  def deleteAttribute(attributeId: Int): Unit = DBConn autoCommit { implicit session =>
    val instanceTable = new InstanceTable(dataset.id)
    val valueTable = new ValueTable(dataset.id)
    sql"DELETE FROM ${AttributeTable.table} WHERE ${AttributeTable.column.dataset} = ${dataset.id} AND ${AttributeTable.column.id} = $attributeId".execute().apply()
    sql"SHOW TABLES LIKE ${valueTable.tableName}".map(_ => true).first().apply.foreach(_ => sql"DELETE FROM ${valueTable.table} WHERE ${valueTable.column.attribute} = $attributeId".execute().apply())
    sql"SHOW TABLES LIKE ${instanceTable.tableName}".map(_ => true).first().apply.foreach(_ => sql"DELETE FROM ${instanceTable.table} WHERE ${instanceTable.column.attribute} = $attributeId".execute().apply())
  }

  /**
    * Get all attributes for the dataset
    *
    * @return list of attributes
    */
  def getAllAttributes: List[AttributeDetail] = DBConn readOnly { implicit session =>
    val a = AttributeTable.syntax("a")
    sql"SELECT ${a.result.*} FROM ${AttributeTable as a} WHERE ${a.dataset} = ${dataset.id} AND ${a.active} = 1".map(AttributeTable(a.resultName)).list().apply()
  }

}

object MysqlAttributeOps {

  /**
    * Create attribute operations instance decorated by validator
    *
    * @param datasetDetail    dataset detail
    * @param mysqlDBConnector mysql database connection
    * @return attribute operations object
    */
  def apply(datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector): AttributeOps = new ValidationAttributeOps(new MysqlAttributeOps(datasetDetail))

}