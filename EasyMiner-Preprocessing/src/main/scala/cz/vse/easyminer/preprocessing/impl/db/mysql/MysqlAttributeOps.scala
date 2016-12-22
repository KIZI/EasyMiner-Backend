package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.preprocessing.impl.db.ValidationAttributeOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeTable, InstanceTable, ValueTable}
import cz.vse.easyminer.preprocessing.{AttributeDetail, AttributeOps, DatasetDetail}
import scalikejdbc._

/**
 * Created by propan on 27. 1. 2016.
 */
class MysqlAttributeOps private[db](val dataset: DatasetDetail)(implicit connector: MysqlDBConnector) extends AttributeOps {

  import connector._

  def renameAttribute(attributeId: Int, newName: String): Unit = DBConn autoCommit { implicit session =>
    sql"UPDATE ${AttributeTable.table} SET ${AttributeTable.column.name} = $newName WHERE ${AttributeTable.column.dataset} = ${dataset.id} AND ${AttributeTable.column.id} = $attributeId".execute().apply()
  }

  def getAttribute(attributeId: Int): Option[AttributeDetail] = DBConn readOnly { implicit session =>
    val a = AttributeTable.syntax("a")
    sql"SELECT ${a.result.*} FROM ${AttributeTable as a} WHERE ${a.dataset} = ${dataset.id} AND ${a.id} = $attributeId AND ${a.active} = 1".map(AttributeTable(a.resultName)).first().apply()
  }

  def deleteAttribute(attributeId: Int): Unit = DBConn autoCommit { implicit session =>
    val instanceTable = new InstanceTable(dataset.id)
    val valueTable = new ValueTable(dataset.id)
    sql"DELETE FROM ${AttributeTable.table} WHERE ${AttributeTable.column.dataset} = ${dataset.id} AND ${AttributeTable.column.id} = $attributeId".execute().apply()
    sql"DELETE FROM ${valueTable.table} WHERE ${valueTable.column.attribute} = $attributeId".execute().apply()
    sql"DELETE FROM ${instanceTable.table} WHERE ${instanceTable.column.attribute} = $attributeId".execute().apply()
  }

  def getAllAttributes: List[AttributeDetail] = DBConn readOnly { implicit session =>
    val a = AttributeTable.syntax("a")
    sql"SELECT ${a.result.*} FROM ${AttributeTable as a} WHERE ${a.dataset} = ${dataset.id} AND ${a.active} = 1".map(AttributeTable(a.resultName)).list().apply()
  }

}

object MysqlAttributeOps {

  def apply(datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector): AttributeOps = new ValidationAttributeOps(new MysqlAttributeOps(datasetDetail))

}