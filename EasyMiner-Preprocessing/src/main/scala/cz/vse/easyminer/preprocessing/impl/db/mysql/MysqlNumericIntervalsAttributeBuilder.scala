package cz.vse.easyminer
package preprocessing.impl.db
package mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.{PersistentLock, TaskStatusProcessor}
import cz.vse.easyminer.data._
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.PersistentLocks
import scalikejdbc._

/**
  * Created by propan on 12. 11. 2016.
  */
class MysqlNumericIntervalsAttributeBuilder private[db](val dataset: DatasetDetail,
                                                        val attributes: Seq[NumericIntervalsAttribute])
                                                       (implicit
                                                        private[db] val mysqlDBConnector: MysqlDBConnector,
                                                        private[db] val taskStatusProcessor: TaskStatusProcessor) extends DbAttributeBuilder[NumericIntervalsAttribute] with DbMysqlTables {

  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.Limited._
  import mysqlDBConnector._

  private[db] val attributeOps: AttributeOps = dataset.toAttributeOps(dataset)

  private[db] val fieldOps: Option[FieldOps] = dataset.toFieldOps

  private[db] def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail] = {
    val fieldIds = attributes.map(_.attributeDetail.field)
    def getAttributeSelect(fieldCol: SQLSyntax) = attributes.foldLeft(sqls"NULL")((select, attributeWithDetail) => sqls"IF($fieldCol = ${attributeWithDetail.attributeDetail.field}, ${attributeWithDetail.attributeDetail.id}, $select)")
    def getSelectCond(valueNominalCol: SQLSyntax, valueNumericCol: SQLSyntax, fieldCol: SQLSyntax) = attributes.foldLeft(sqls"$valueNominalCol") { (select, attributeWithDetail) =>
      attributeWithDetail.attribute.bins.foldLeft(select) { (select, bin) =>
        val sqlEnum = bin.intervals.map(x => sqls"$valueNumericCol ${getNumericComparator(x.from, true)} ${x.from.value} AND $valueNumericCol ${getNumericComparator(x.to, false)} ${x.to.value}").reduce(_ or _)
        sqls"IF($fieldCol = ${attributeWithDetail.attributeDetail.field} AND ($sqlEnum), ${bin.name}, $select)"
      }
    }
    DBConn autoCommit { implicit session =>
      taskStatusProcessor.newStatus(s"Aggregated values are now populating with indexing...")
      val maxValueId = sql"SELECT MAX(${preprocessingValueTable.column.id}) FROM ${preprocessingValueTable.table}".map(_.intOpt(1)).first().apply().flatten.getOrElse(0)
      val selectStage1 = sqls"SELECT ${getAttributeSelect(dataValueTable.column.field("field"))} AS a, ${getSelectCond(dataValueTable.column.valueNominal, dataValueTable.column.valueNumeric, dataValueTable.column.field("field"))} AS nom, ${dataValueTable.column.frequency} AS f FROM ${dataValueTable.table} WHERE ${dataValueTable.column.field("field")} IN ($fieldIds) AND ${dataValueTable.column.valueNumeric} IS NOT NULL ORDER BY ${dataValueTable.column.field("field")}, ${dataValueTable.column.id}"
      val selectStage2 = sqls"SELECT s1.a, s1.nom, SUM(s1.f) FROM ($selectStage1) s1 GROUP BY s1.a, s1.nom"
      sql"INSERT INTO ${preprocessingValueTable.table} (${preprocessingValueTable.column.columns}) SELECT @rownum := @rownum + 1 AS rank, t.* FROM ($selectStage2) t, (SELECT @rownum := $maxValueId) r".execute().apply()
      taskStatusProcessor.newStatus(s"Attribute columns are now populating...")
      val selectStage3 = sqls"SELECT ${dataInstanceTable.column.id}, ${getAttributeSelect(dataInstanceTable.column.field("field"))} AS a, ${getSelectCond(dataInstanceTable.column.valueNominal, dataInstanceTable.column.valueNumeric, dataInstanceTable.column.field("field"))} AS nom FROM ${dataInstanceTable.table} WHERE ${dataInstanceTable.column.field("field")} IN ($fieldIds) AND ${dataInstanceTable.column.valueNumeric} IS NOT NULL"
      sql"""
      INSERT INTO ${preprocessingInstanceTable.table} (${preprocessingInstanceTable.column.columns})
      SELECT s3.id, ${pv.result.attribute}, ${pv.result.id}
      FROM ($selectStage3) s3
      INNER JOIN ${preprocessingValueTable as pv} ON (${pv.attribute} = s3.a AND ${pv.value} = s3.nom)
      """.execute().apply()
      val attributeFreqMap = sql"SELECT ${preprocessingValueTable.column.attribute}, COUNT(${preprocessingValueTable.column.id}) AS freq FROM ${preprocessingValueTable.table} GROUP BY ${preprocessingValueTable.column.attribute}"
        .map(wrs => wrs.int(preprocessingValueTable.column.attribute) -> wrs.int("freq")).list().apply().toMap
      attributes.map(attributeWithDetail => attributeWithDetail.attributeDetail.copy(uniqueValuesSize = attributeFreqMap(attributeWithDetail.attributeDetail.id)))
    }
  }

  override def attributeIsValid(attribute: NumericIntervalsAttribute, fieldDetail: FieldDetail): Boolean = fieldDetail.`type` == NumericFieldType

  override private[db] def buildWrapper(f: => Seq[AttributeDetail]): Seq[AttributeDetail] = PersistentLock(PersistentLocks.datasetLockName(dataset.id)) {
    f
  }

}

object MysqlNumericIntervalsAttributeBuilder {

  def apply(attributes: Seq[NumericIntervalsAttribute], datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): AttributeBuilder[NumericIntervalsAttribute] = {
    new ValidationAttributeBuilder(
      new MysqlNumericIntervalsAttributeBuilder(datasetDetail, attributes)
    )
  }

}