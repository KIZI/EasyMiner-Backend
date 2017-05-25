/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer
package preprocessing.impl
package db
package mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.{PersistentLock, TaskStatusProcessor}
import cz.vse.easyminer.data._
import cz.vse.easyminer.preprocessing.{AttributeDetail, _}
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 12. 11. 2016.
  */
class MysqlEquidistantIntervalsAttributeBuilder private[db](val dataset: DatasetDetail,
                                                            val attributes: Seq[EquidistantIntervalsAttribute])
                                                           (implicit
                                                            private[db] val mysqlDBConnector: MysqlDBConnector,
                                                            private[db] val taskStatusProcessor: TaskStatusProcessor) extends DbAttributeBuilder[EquidistantIntervalsAttribute] with DbMysqlTables {

  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.Limited._
  import mysqlDBConnector._

  private[db] val attributeOps: AttributeOps = dataset.toAttributeOps(dataset)

  private[db] val fieldOps: Option[FieldOps] = dataset.toFieldOps

  case class AttributeStats(attributeWithDetail: AttributeWithDetail, fieldNumericDetail: FieldNumericDetail) {
    val intervalSize = (fieldNumericDetail.max - fieldNumericDetail.min) / attributeWithDetail.attribute.bins
  }

  case class Bin(attributeWithDetail: AttributeWithDetail, valueId: Int, interval: NumericIntervalsAttribute.Interval, frequency: Int)

  object Bin {

    implicit object BinOrdering extends Ordering[Bin] {
      def compare(x: Bin, y: Bin): Int = Ordering[(Int, Double)].compare(x.attributeWithDetail.attributeDetail.id -> x.interval.from.value, y.attributeWithDetail.attributeDetail.id -> y.interval.from.value)
    }

    def apply(attributeStats: AttributeStats, from: Double, frequency: Int): Bin = {
      val to = from + attributeStats.intervalSize + attributeStats.intervalSize / 2
      val toBorder = if (to >= attributeStats.fieldNumericDetail.max) InclusiveIntervalBorder(attributeStats.fieldNumericDetail.max) else ExclusiveIntervalBorder(intf(attributeStats)(to))
      Bin(attributeStats.attributeWithDetail, 0, NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(from), toBorder), frequency)
    }
  }

  private def intf(attributeStats: AttributeStats)(value: Double) = value - ((value - attributeStats.fieldNumericDetail.min) % attributeStats.intervalSize)

  private def fetchBins(attributes: Seq[AttributeWithDetail], fieldIds: Seq[Int])(implicit session: DBSession) = {
    val ds = dataFieldNumericDetailTable.syntax
    val attributesStats = sql"SELECT ${ds.result.*} FROM ${dataFieldNumericDetailTable as ds} WHERE ${ds.id} IN ($fieldIds)"
      .map(dataFieldNumericDetailTable.apply(ds.resultName)).list().apply()
      .map(fieldNumericDetail => AttributeStats(attributes.find(_.attributeDetail.field == fieldNumericDetail.id).get, fieldNumericDetail))
    val maxValueId = sql"SELECT MAX(${preprocessingValueTable.column.id}) FROM ${preprocessingValueTable.table}".map(_.intOpt(1)).first().apply().flatten.getOrElse(0)
    val intervals = collection.mutable.Map(
      attributesStats.map { as =>
        val step = as.intervalSize / 2
        val aintf = intf(as) _
        val bins = collection.mutable.Map((0 until as.attributeWithDetail.attribute.bins).map(i => aintf(as.fieldNumericDetail.min + step + as.intervalSize * i) -> 0): _*)
        as.attributeWithDetail.attributeDetail.field ->(as, bins)
      }: _*
    )
    sql"""
      SELECT ${dataValueTable.column.field("field")} AS f, ${dataValueTable.column.valueNumeric} AS v, ${dataValueTable.column.frequency} AS freq
      FROM ${dataValueTable.table}
      WHERE ${dataValueTable.column.field("field")} IN ($fieldIds) AND ${dataValueTable.column.valueNumeric} IS NOT NULL
      """.foreach { wrs =>
      val (f, v, freq) = (wrs.int("f"), wrs.double("v"), wrs.int("freq"))
      val (a, i) = intervals(f)
      val from = if (v == a.fieldNumericDetail.max) intf(a)(v - a.intervalSize / 2) else intf(a)(v)
      i.update(from, i(from) + freq)
    }
    intervals.valuesIterator.flatMap { case (a, m) =>
      m.map(x => Bin(a, x._1, x._2))
    }.toList.sorted.iterator.zipWithIndex.map { case (bin, i) =>
      bin.copy(valueId = maxValueId + i + 1)
    }.toList
  }

  private[db] def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail] = DBConn autoCommit { implicit session =>
    val fieldIds = attributes.map(_.attributeDetail.field)
    val bins = fetchBins(attributes, fieldIds)
    def getAttributeSelect(fieldCol: SQLSyntax) = attributes.foldLeft(sqls"NULL")((select, attributeWithDetail) => sqls"IF($fieldCol = ${attributeWithDetail.attributeDetail.field}, ${attributeWithDetail.attributeDetail.id}, $select)")
    def getSelectCond(valueNumericCol: SQLSyntax, fieldCol: SQLSyntax) = bins.foldLeft(sqls"NULL") { case (select, bin) =>
      sqls"IF($fieldCol = ${bin.attributeWithDetail.attributeDetail.field} AND $valueNumericCol ${getNumericComparator(bin.interval.from, true)} ${bin.interval.from.value} AND $valueNumericCol ${getNumericComparator(bin.interval.to, false)} ${bin.interval.to.value}, ${bin.valueId}, $select)"
    }
    SQL(sqls"INSERT INTO ${preprocessingValueTable.table} (${preprocessingValueTable.column.columns}) VALUES (?, ?, ?, ?)".value).batch(bins.map(bin =>
      List(bin.valueId, bin.attributeWithDetail.attributeDetail.id, bin.interval.toIntervalString, bin.frequency)
    ): _*).apply()
    sql"""
      INSERT INTO ${preprocessingInstanceTable.table} (${preprocessingInstanceTable.column.columns})
      SELECT ${dataInstanceTable.column.id}, ${getAttributeSelect(dataInstanceTable.column.field("field"))}, ${getSelectCond(dataInstanceTable.column.valueNumeric, dataInstanceTable.column.field("field"))} FROM ${dataInstanceTable.table} WHERE ${dataInstanceTable.column.field("field")} IN ($fieldIds) AND ${dataInstanceTable.column.valueNumeric} IS NOT NULL
    """.execute().apply()
    bins.groupBy(_.attributeWithDetail.attributeDetail).map { case (attribute, bins) => attribute.copy(uniqueValuesSize = bins.length) }.toList
  }

  override def attributeIsValid(attribute: EquidistantIntervalsAttribute, fieldDetail: FieldDetail): Boolean = fieldDetail.`type` == NumericFieldType

  override private[db] def buildWrapper(f: => Seq[AttributeDetail]): Seq[AttributeDetail] = PersistentLock(PersistentLocks.datasetLockName(dataset.id)) {
    f
  }

}

object MysqlEquidistantIntervalsAttributeBuilder {

  def apply(attributes: Seq[EquidistantIntervalsAttribute], datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): AttributeBuilder[EquidistantIntervalsAttribute] = {
    new ValidationAttributeBuilder(
      new MysqlEquidistantIntervalsAttributeBuilder(datasetDetail, attributes)
    )
  }

}
