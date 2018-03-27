/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer
package preprocessing.impl.db
package mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.{PersistentLock, TaskStatusProcessor}
import cz.vse.easyminer.data.{NominalFieldType, NumericFieldType}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.PersistentLocks
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 22. 12. 2015.
  */

/**
  * This builds attributes from fields as the same copy
  *
  * @param dataset             dataset detail
  * @param attributes          input attribute definitions
  * @param mysqlDBConnector    mysql database connections
  * @param taskStatusProcessor task processor for monitoring
  */
class MysqlSimpleAttributeBuilder private(val dataset: DatasetDetail,
                                              val attributes: Seq[SimpleAttribute])
                                             (implicit
                                              protected val mysqlDBConnector: MysqlDBConnector,
                                              protected val taskStatusProcessor: TaskStatusProcessor) extends DbAttributeBuilder[SimpleAttribute] with DbMysqlTables {

  import mysqlDBConnector._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.Limited._

  protected lazy val attributeOps = dataset.toAttributeOps(dataset)

  protected lazy val fieldOps = dataset.toFieldOps

  protected def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail] = {
    //filter of instances (joined preprocessing value table and data instance table)
    //preprocessing attribute == current attribute AND data instance field id = current field
    val dataWhere = attributes.map(attributeWithDetail => sqls"${pv.attribute} = ${attributeWithDetail.attributeDetail.id} AND ${di.field("field")} = ${attributeWithDetail.attributeDetail.field}").reduce(_ or _)
    //if data field id == current attribute field then return attribute id then next attribute comparison
    val attributeSelect = attributes.foldLeft(sqls"NULL")((select, attributeWithDetail) => sqls"IF(${dataValueTable.column.field("field")} = ${attributeWithDetail.attributeDetail.field}, ${attributeWithDetail.attributeDetail.id}, $select)")
    DBConn autoCommit { implicit session =>
      taskStatusProcessor.newStatus(s"Aggregated values are now populating with indexing...")
      //get max id
      val maxValueId = sql"SELECT MAX(${preprocessingValueTable.column.id}) FROM ${preprocessingValueTable.table}".map(_.intOpt(1)).first().apply().flatten.getOrElse(0)
      //insert to preprocessing value table - copy values, only nominal or only numeric
      val valueNominalOrNumericWhere = attributes.iterator.map { attributeWithDetail =>
        val fieldEquality = sqls"${dataValueTable.column.field("field")} = ${attributeWithDetail.fieldDetail.id}"
        attributeWithDetail.fieldDetail.`type` match {
          case NominalFieldType => fieldEquality
          case NumericFieldType => fieldEquality and sqls"${dataValueTable.column.valueNumeric} IS NOT NULL"
        }
      }.reduce(_ or _)
      val select = sqls"SELECT $attributeSelect, ${dataValueTable.column.valueNominal}, ${dataValueTable.column.frequency} FROM ${dataValueTable.table} WHERE $valueNominalOrNumericWhere ORDER BY ${dataValueTable.column.field("field")}, ${dataValueTable.column.id}"
      sql"INSERT INTO ${preprocessingValueTable.table} (${preprocessingValueTable.column.columns}) SELECT @rownum := @rownum + 1 AS rank, t.* FROM ($select) t, (SELECT @rownum := $maxValueId) r".execute().apply()
      taskStatusProcessor.newStatus(s"Attribute columns are now populating...")
      //insert to preprocessing instance table
      sql"""
      INSERT INTO ${preprocessingInstanceTable.table} (${preprocessingInstanceTable.column.id}, ${preprocessingInstanceTable.column.attribute}, ${preprocessingInstanceTable.column.value})
      SELECT ${di.result.id}, ${pv.result.attribute}, ${pv.result.id}
      FROM ${dataInstanceTable as di}
      INNER JOIN ${preprocessingValueTable as pv} ON (${di.valueNominal} = ${pv.value})
      WHERE $dataWhere
      """.execute().apply()
    }
    attributes.map(_.attributeDetail)
  }

  override protected def buildWrapper(f: => Seq[AttributeDetail]): Seq[AttributeDetail] = PersistentLock(PersistentLocks.datasetLockName(dataset.id)) {
    f
  }

}

object MysqlSimpleAttributeBuilder {

  import DatasetTypeConversions.Limited._

  def apply(attributes: Seq[SimpleAttribute], datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): AttributeBuilder[SimpleAttribute] = {
    new ValidationAttributeBuilder(
      new MysqlSimpleAttributeBuilder(datasetDetail, attributes)
    )
  }

}