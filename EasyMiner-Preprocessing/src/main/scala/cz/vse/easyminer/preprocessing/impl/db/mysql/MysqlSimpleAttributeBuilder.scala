package cz.vse.easyminer
package preprocessing.impl.db
package mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.{PersistentLock, TaskStatusProcessor}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.PersistentLocks
import scalikejdbc._

/**
  * Created by propan on 22. 12. 2015.
  */
class MysqlSimpleAttributeBuilder private[db](val dataset: DatasetDetail,
                                              val attributes: Seq[SimpleAttribute])
                                             (implicit
                                              private[db] val mysqlDBConnector: MysqlDBConnector,
                                              private[db] val taskStatusProcessor: TaskStatusProcessor) extends DbAttributeBuilder[SimpleAttribute] with DbMysqlTables {

  import mysqlDBConnector._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.Limited._

  private[db] lazy val attributeOps = dataset.toAttributeOps

  private[db] lazy val fieldOps = dataset.toFieldOps

  private[db] def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail] = {
    val fieldIds = attributes.map(_.attributeDetail.field)
    val dataWhere = attributes.map(attributeWithDetail => sqls"${pv.attribute} = ${attributeWithDetail.attributeDetail.id} AND ${di.field("field")} = ${attributeWithDetail.attributeDetail.field}").reduce(_ or _)
    val attributeSelect = attributes.foldLeft(sqls"NULL")((select, attributeWithDetail) => sqls"IF(${dataValueTable.column.field("field")} = ${attributeWithDetail.attributeDetail.field}, ${attributeWithDetail.attributeDetail.id}, $select)")
    DBConn autoCommit { implicit session =>
      taskStatusProcessor.newStatus(s"Aggregated values are now populating with indexing...")
      val maxValueId = sql"SELECT MAX(${preprocessingValueTable.column.id}) FROM ${preprocessingValueTable.table}".map(_.intOpt(1)).first().apply().flatten.getOrElse(0)
      val select = sqls"SELECT $attributeSelect, ${dataValueTable.column.valueNominal}, ${dataValueTable.column.valueNumeric}, ${dataValueTable.column.frequency} FROM ${dataValueTable.table} WHERE ${dataValueTable.column.field("field")} IN ($fieldIds) ORDER BY ${dataValueTable.column.field("field")}, ${dataValueTable.column.id}"
      sql"INSERT INTO ${preprocessingValueTable.table} (${preprocessingValueTable.column.columns}) SELECT @rownum := @rownum + 1 AS rank, t.* FROM ($select) t, (SELECT @rownum := $maxValueId) r".execute().apply()
      taskStatusProcessor.newStatus(s"Attribute columns are now populating...")
      sql"""
      INSERT INTO ${preprocessingInstanceTable.table} (${preprocessingInstanceTable.column.id}, ${preprocessingInstanceTable.column.attribute}, ${preprocessingInstanceTable.column.value})
      SELECT ${di.result.id}, ${pv.result.attribute}, ${pv.result.id}
      FROM ${dataInstanceTable as di}
      INNER JOIN ${preprocessingValueTable as pv} ON (COALESCE(${di.valueNumeric}, ${di.valueNominal}) = COALESCE(${pv.valueNumeric}, ${pv.valueNominal}))
      WHERE $dataWhere
      """.execute().apply()
    }
    attributes.map(_.attributeDetail)
  }

  override private[db] def buildWrapper(f: => Seq[AttributeDetail]): Seq[AttributeDetail] = PersistentLock(PersistentLocks.datasetLockName(dataset.id)) {
    f
  }

}

object MysqlSimpleAttributeBuilder {

  def apply(attributes: Seq[SimpleAttribute], datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): AttributeBuilder[SimpleAttribute] = {
    new ValidationAttributeBuilder(
      new MysqlSimpleAttributeBuilder(datasetDetail, attributes)
    )
  }

}