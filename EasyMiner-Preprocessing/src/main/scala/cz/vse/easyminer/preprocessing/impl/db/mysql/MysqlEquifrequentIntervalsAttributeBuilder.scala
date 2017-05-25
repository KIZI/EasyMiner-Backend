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
import cz.vse.easyminer.data.{NumericValueDetail, _}
import cz.vse.easyminer.preprocessing.NumericIntervalsAttribute.Interval
import cz.vse.easyminer.preprocessing.{AttributeBuilder, _}
import scalikejdbc._


/**
  * Created by Vaclav Zeman on 25. 11. 2016.
  */
class MysqlEquifrequentIntervalsAttributeBuilder private[db](val dataset: DatasetDetail,
                                                             val attributes: Seq[EquifrequentIntervalsAttribute])
                                                            (implicit
                                                             private[db] val mysqlDBConnector: MysqlDBConnector,
                                                             private[db] val taskStatusProcessor: TaskStatusProcessor) extends DbAttributeBuilder[EquifrequentIntervalsAttribute] with DbMysqlTables with IntervalOps {

  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.Limited._
  import mysqlDBConnector._

  private[db] val attributeOps: AttributeOps = dataset.toAttributeOps(dataset)

  private[db] val fieldOps: Option[FieldOps] = dataset.toFieldOps

  case class AttributeIntervals(attributeWithDetail: AttributeWithDetail, intervals: collection.mutable.ArrayBuffer[AttributeInterval]) {
    lazy val maxFrequency = math.ceil(dataset.size.toDouble / attributeWithDetail.attribute.bins)
  }

  private def initIntervals(attributes: Seq[AttributeWithDetail], fieldIds: Seq[Int])(implicit session: DBSession) = {
    val dv = dataValueTable.syntax
    val sqlValues = sql"SELECT ${dv.result.*} FROM ${dataValueTable as dv} WHERE ${dv.field("field")} IN ($fieldIds) AND ${dataValueTable.column.valueNumeric} IS NOT NULL ORDER BY ${dv.field("field")}, ${dv.valueNumeric}"
    sqlValues.foldLeft(List.empty[(AttributeIntervals, Int)]) { (attributeIntervals, wrs) =>
      val valueNumeric = dataValueTable(dv.resultName, NumericFieldType)(wrs).asInstanceOf[data.NumericValueDetail]
      val attributeIntervalsList = if (attributeIntervals.isEmpty || attributeIntervals.head._1.attributeWithDetail.attributeDetail.field != valueNumeric.field) {
        val attribute = attributes.find(_.attributeDetail.field == valueNumeric.field).get
        (AttributeIntervals(attribute, new collection.mutable.ArrayBuffer(attribute.attribute.bins)), 0) :: attributeIntervals
      } else {
        attributeIntervals
      }
      val (currentAttributeIntervals, processedFields) = attributeIntervalsList.head
      val fieldsMinusBins = currentAttributeIntervals.attributeWithDetail.fieldDetail.uniqueValuesSize - processedFields - currentAttributeIntervals.attributeWithDetail.attribute.bins + currentAttributeIntervals.intervals.length
      currentAttributeIntervals.intervals.lastOption.filter(interval => fieldsMinusBins > 0
        && (currentAttributeIntervals.intervals.length == currentAttributeIntervals.attributeWithDetail.attribute.bins
        || math.abs(currentAttributeIntervals.maxFrequency - interval.frequency - valueNumeric.frequency) < math.abs(currentAttributeIntervals.maxFrequency - interval.frequency))
      ) match {
        case Some(interval) => currentAttributeIntervals.intervals.update(currentAttributeIntervals.intervals.length - 1, AttributeInterval(Interval(interval.interval.from, InclusiveIntervalBorder(valueNumeric.value)), valueNumeric.frequency + interval.frequency))
        case None => currentAttributeIntervals.intervals += AttributeInterval(Interval(InclusiveIntervalBorder(valueNumeric.value), InclusiveIntervalBorder(valueNumeric.value)), valueNumeric.frequency)
      }
      (currentAttributeIntervals, processedFields + 1) :: attributeIntervalsList.tail
    }.map(_._1)
  }

  private def smoothIntervals(attributeIntervals: AttributeIntervals)(implicit session: DBSession) = {
    //go from right (greater) interval to left (lower) interval
    //intervals: [f1, t1], [f2, t2], ...,  [f{pointer-1}, t{pointer-1}], [f{pointer}, t{pointer}]
    //
    smoothAttributeIntervals(attributeIntervals.intervals, new Traversable[data.NumericValueDetail] {
      def foreach[U](f: (NumericValueDetail) => U): Unit = {
        val dv = dataValueTable.syntax
        val sqlValues = sql"SELECT ${dv.result.*} FROM ${dataValueTable as dv} WHERE ${dv.field("field")} = ${attributeIntervals.attributeWithDetail.attributeDetail.field} AND ${dv.valueNumeric} IS NOT NULL ORDER BY ${dv.valueNumeric} DESC"
        sqlValues.foreach(wrs => f(dataValueTable(dv.resultName, NumericFieldType)(wrs).asInstanceOf[data.NumericValueDetail]))
      }
    }) { (movedValue, prevInterval, currentInterval) =>
      //                                     * - optimal                          * - optimal
      //before: prevInterval[f1, t1;----______] < currentInterval(f2, t2;--------------)
      //                                ^-------------<-move-<-----------^^^^ <- moved part is valueNumeric == f2
      //after:  prevInterval[f1, t1;--------__] < currentInterval(f2, t2;----------)
      //currentScore = ----_____* + ---------*----
      //                   ^^^^^^ +          ^^^^^ = distance current frequency from optimal = 6 + 5 = 11
      //nextScore    = --------_* + ---------*
      //                       ^^ +          ^ = 2 + 1 = 3
      //if nextScore is lower than currentScore, make shift!
      //go to the next interval
      val currentScore = math.abs(attributeIntervals.maxFrequency - currentInterval.frequency) + math.abs(attributeIntervals.maxFrequency - prevInterval.frequency)
      val nextScore = math.abs(attributeIntervals.maxFrequency - currentInterval.frequency + movedValue.frequency) + math.abs(attributeIntervals.maxFrequency - prevInterval.frequency - movedValue.frequency)
      val currentDifference = math.abs(currentInterval.frequency - prevInterval.frequency)
      val nextDifference = math.abs((currentInterval.frequency - movedValue.frequency) - (prevInterval.frequency + movedValue.frequency))
      nextScore <= currentScore && nextDifference < currentDifference
    } { (movedValue, prevInterval, currentInterval) =>
      //before: prevInterval[f1, t1;--------------] > currentInterval(f2, t2;----______)
      //                                      ^^^^--------->-move->--------------^ <- moved part is lastValue == t1
      val currentScore = math.abs(attributeIntervals.maxFrequency - currentInterval.frequency) + math.abs(attributeIntervals.maxFrequency - prevInterval.frequency)
      val nextScore = math.abs(attributeIntervals.maxFrequency - currentInterval.frequency - movedValue.frequency) + math.abs(attributeIntervals.maxFrequency - prevInterval.frequency + movedValue.frequency)
      val currentDifference = math.abs(currentInterval.frequency - prevInterval.frequency)
      val nextDifference = math.abs((currentInterval.frequency + movedValue.frequency) - (prevInterval.frequency - movedValue.frequency))
      nextScore <= currentScore && nextDifference < currentDifference
    }
  }

  private[db] def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail] = DBConn autoCommit { implicit session =>
    val fieldIds = attributes.map(_.attributeDetail.field)
    val attributesIntervals = initIntervals(attributes, fieldIds)
    for (attributeIntervals <- attributesIntervals if attributeIntervals.intervals.length > 1) {
      smoothIntervals(attributeIntervals)
      for (i <- 0 until (attributeIntervals.intervals.length - 1)) {
        val leftInterval = attributeIntervals.intervals(i)
        val rightInterval = attributeIntervals.intervals(i + 1)
        val mergedCutPoint = (leftInterval.interval.to.value + rightInterval.interval.from.value) / 2.0
        attributeIntervals.intervals.update(i, leftInterval.copy(interval = leftInterval.interval.copy(to = ExclusiveIntervalBorder(mergedCutPoint))))
        attributeIntervals.intervals.update(i + 1, rightInterval.copy(interval = rightInterval.interval.copy(from = InclusiveIntervalBorder(mergedCutPoint))))
      }
    }
    val maxValueId = sql"SELECT MAX(${preprocessingValueTable.column.id}) FROM ${preprocessingValueTable.table}".map(_.intOpt(1)).first().apply().flatten.getOrElse(0)
    val intervals = attributesIntervals.flatMap(attributeIntervals => attributeIntervals.intervals.map(_ -> attributeIntervals.attributeWithDetail.attributeDetail))
    def getAttributeSelect(fieldCol: SQLSyntax) = attributes.foldLeft(sqls"NULL")((select, attributeWithDetail) => sqls"IF($fieldCol = ${attributeWithDetail.attributeDetail.field}, ${attributeWithDetail.attributeDetail.id}, $select)")
    def getSelectCond(valueNumericCol: SQLSyntax, fieldCol: SQLSyntax) = intervals.iterator.zipWithIndex.foldLeft(sqls"NULL") { case (select, ((interval, attributeDetail), id)) =>
      sqls"IF($fieldCol = ${attributeDetail.field} AND $valueNumericCol ${getNumericComparator(interval.interval.from, true)} ${interval.interval.from.value} AND $valueNumericCol ${getNumericComparator(interval.interval.to, false)} ${interval.interval.to.value}, ${id + maxValueId + 1}, $select)"
    }
    SQL(sqls"INSERT INTO ${preprocessingValueTable.table} (${preprocessingValueTable.column.columns}) VALUES (?, ?, ?, ?)".value).batch(intervals.iterator.zipWithIndex.map {
      case ((interval, attributeDetail), id) => List(id + maxValueId + 1, attributeDetail.id, interval.interval.toIntervalString, interval.frequency)
    }.toList: _*).apply()
    sql"""
      INSERT INTO ${preprocessingInstanceTable.table} (${preprocessingInstanceTable.column.columns})
      SELECT ${dataInstanceTable.column.id}, ${getAttributeSelect(dataInstanceTable.column.field("field"))}, ${getSelectCond(dataInstanceTable.column.valueNumeric, dataInstanceTable.column.field("field"))} FROM ${dataInstanceTable.table} WHERE ${dataInstanceTable.column.field("field")} IN ($fieldIds) AND ${dataInstanceTable.column.valueNumeric} IS NOT NULL
    """.execute().apply()
    attributesIntervals.map(attributeIntervals => attributeIntervals.attributeWithDetail.attributeDetail.copy(uniqueValuesSize = attributeIntervals.intervals.length))
  }

  override def attributeIsValid(attribute: EquifrequentIntervalsAttribute, fieldDetail: FieldDetail): Boolean = fieldDetail.`type` == NumericFieldType

  override private[db] def buildWrapper(f: => Seq[AttributeDetail]): Seq[AttributeDetail] = PersistentLock(PersistentLocks.datasetLockName(dataset.id)) {
    f
  }

}

object MysqlEquifrequentIntervalsAttributeBuilder {

  def apply(attributes: Seq[EquifrequentIntervalsAttribute], datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): AttributeBuilder[EquifrequentIntervalsAttribute] = {
    new ValidationAttributeBuilder(
      new MysqlEquifrequentIntervalsAttributeBuilder(datasetDetail, attributes)
    )
  }

}
