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
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.IntervalBorderOps._
import scalikejdbc._

import scala.collection.{mutable, _}
import scala.language.implicitConversions

/**
  * Created by Vaclav Zeman on 12. 11. 2016.
  */

/**
  * Class for creation attribute from numeric field by user-specified intervals
  * It creates intervals from numbers
  *
  * @param dataset             dataset detail
  * @param attributes          input attribute definitions
  * @param mysqlDBConnector    mysql database connections
  * @param taskStatusProcessor task processor for monitoring
  */
class MysqlNumericIntervalsAttributeBuilder private(val dataset: DatasetDetail,
                                                    val attributes: Seq[NumericIntervalsAttribute])
                                                   (implicit
                                                    protected val mysqlDBConnector: MysqlDBConnector,
                                                    protected val taskStatusProcessor: TaskStatusProcessor) extends DbAttributeBuilder[NumericIntervalsAttribute] with DbMysqlTables {

  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.Limited._
  import mysqlDBConnector._

  protected val attributeOps: AttributeOps = dataset.toAttributeOps(dataset)

  protected val fieldOps: Option[FieldOps] = dataset.toFieldOps

  private def populateValueTable(attributes: Seq[AttributeWithDetail], fieldMap: Map[Int, AttributeWithDetail])(implicit session: DBSession) = {
    type IntervalWithBin = (AttributeDetail, NumericIntervalsAttribute.Interval, String)
    type FreqWithId = (Int, Int)
    type AttributeWithBin = (Int, String)
    //intervals queue sorted by field id and interval min value in ascending
    //each interval has also assigned attribute detail and bin value
    val sortedIntervals = mutable.PriorityQueue.empty[IntervalWithBin](Ordering.by[IntervalWithBin, (Int, Double)](iwb => (iwb._1.field, iwb._2.from.value)).reverse)
    for {
      attributeWithDetail <- attributes
      bin <- attributeWithDetail.attribute.bins
      interval <- bin.intervals
    } {
      sortedIntervals.enqueue((attributeWithDetail.attributeDetail, interval, bin.name))
    }
    //map for quick access to attribute-bin and their frequencies and ids
    //this map is also used for iterative frequencies and ids update
    val binFreqs = mutable.Map.empty[AttributeWithBin, FreqWithId]
    for {
      attributeWithDetail <- attributes
      bin <- attributeWithDetail.attribute.bins
    } {
      binFreqs += ((attributeWithDetail.attributeDetail.id, bin.name) ->(0, 0))
    }
    //get max id from preprocessing value table
    val maxValueId = sql"SELECT MAX(${preprocessingValueTable.column.id}) FROM ${preprocessingValueTable.table}".map(_.intOpt(1)).first().apply().flatten.getOrElse(0)
    //this is prepared insert query for batching insert
    val preparedValueInsertQuery = sql"INSERT INTO ${preprocessingValueTable.table} (${preprocessingValueTable.column.columns}) VALUES (?, ?, ?, ?)"
    //this is a buffer for batching insert - max buffer size is 100 inserts
    //after filling the buffer is flushed
    val insertBatch = mutable.ListBuffer.empty[Seq[Any]]
    //this only updates binFreqs with a specific id
    //if the bin has id greater than zero then no updates and return the same id
    //if the bin has zero id then update the bin id and return id + 1
    def updateBinId(iwb: IntervalWithBin, id: Int) = {
      val (attributeDetail, _, bin) = iwb
      val key = attributeDetail.id -> bin
      val (frequency, oldId) = binFreqs(key)
      if (oldId > 0) {
        id
      } else {
        binFreqs.update(key, frequency -> id)
        id + 1
      }
    }
    //this method adds insert into the batching buffer
    def insertToBatch(id: Int, attribute: Int, bin: String, frequency: Int) = {
      insertBatch += List(id, attribute, bin, frequency)
      if (insertBatch.length >= 100) flushBatch()
    }
    //flush the insert batching buffer = execute all inserts in batch and clear the buffer
    def flushBatch() = {
      preparedValueInsertQuery.batch(insertBatch: _*).apply()
      insertBatch.clear()
    }
    val dv = dataValueTable.syntax("dv")
    //read all numeric values for specific fields and datasource ordered by field and number in ascending
    //in each iteration the inner function can update current id for the current value and finally it returns last updated id
    var newId = sql"SELECT ${dv.result.*} FROM ${dataValueTable as dv} WHERE ${dv.field("field")} IN (${fieldMap.keys}) AND ${dv.valueNumeric} IS NOT NULL ORDER BY ${dv.field("field")}, ${dv.valueNumeric}".foldLeft(maxValueId + 1) { (id, wrs) =>
      //current numeric value detail
      val value = dataValueTable(dv.resultName, NumericFieldType)(wrs).asInstanceOf[NumericValueDetail]
      //attribute with detail from field id
      val awd = fieldMap(wrs.int(dv.resultName.field("field")))
      //during this iteration we can update preprocessing value id several times - variable is needed
      var newId = id
      while (sortedIntervals.nonEmpty && !sortedIntervals.headOption.exists(iwb => iwb._1.id == awd.attributeDetail.id && iwb._2.to >= value.value)) {
        //if current value has different attribute id or is greater the max border of the current interval then:
        //we move to the next interval and we update the current interval with the actual id
        //then we need to increase value id
        newId = updateBinId(sortedIntervals.dequeue(), newId)
      }
      sortedIntervals.headOption match {
        case Some((attributeDetail, interval, bin)) if interval.from <= value.value =>
          //current value is inside of the actual interval
          //we add frequency of this value to the bin of this interval
          val (currentFrequency, id) = binFreqs(attributeDetail.id -> bin)
          binFreqs.update(attributeDetail.id -> bin, (currentFrequency + value.frequency) -> id)
        case _ => if (awd.attribute.features.contains(PreserveUncovered)) {
          //if the current value is not inside of the actual interval then it is not included in any interval
          //and if the "preserve uncovered" flag is enabled then we create bin from this value (we preserve this value in preprocessing)
          //this value is added into the insert batch and the current value id is increasing
          insertToBatch(newId, awd.attributeDetail.id, value.original, value.frequency)
          newId += 1
        }
      }
      newId
    }
    while (sortedIntervals.nonEmpty) {
      //if some intervals still remain and do not contain any value, then we still want to save them as empty value (with zero frequency).
      newId = updateBinId(sortedIntervals.dequeue(), newId)
    }
    for (((attribute, bin), (frequency, id)) <- binFreqs) {
      //we add all bins with frequencies and updated ids into insert batch
      insertToBatch(id, attribute, bin, frequency)
    }
    //we need to flush all remaining inserts from batch and we clear the binFreqs
    binFreqs.clear()
    flushBatch()
  }

  private def populateInstanceTable(attributes: Seq[AttributeWithDetail], fieldMap: Map[Int, AttributeWithDetail])(implicit session: DBSession) = {
    //this method returns select projection query for attributes ids from fields ids
    def getAttributeSelect(fieldCol: SQLSyntax) = attributes.foldLeft(sqls"NULL")((select, attributeWithDetail) => sqls"IF($fieldCol = ${attributeWithDetail.attributeDetail.field}, ${attributeWithDetail.attributeDetail.id}, $select)")
    //this method returns select projection query for intervals
    //first it uses field condition = IF field.id == attribute.field THEN:
    //second condition is interval enumeration = IF number > interval.min AND number < interval.max THEN assign bin name value
    def getSelectCond(valueNominalCol: SQLSyntax, valueNumericCol: SQLSyntax, fieldCol: SQLSyntax) = attributes.foldLeft(sqls"null") { (select, attributeWithDetail) =>
      val uncoveredValue = if (attributeWithDetail.attribute.features.contains(PreserveUncovered)) valueNominalCol else sqls"null"
      val attributeBinsConds = attributeWithDetail.attribute.bins.foldLeft(uncoveredValue) { (select, bin) =>
        val sqlEnum = bin.intervals.map { x =>
          val from = if (x.from.value == Double.NegativeInfinity) None else Some(x.from |<=| valueNumericCol)
          val to = if (x.to.value == Double.PositiveInfinity) None else Some(x.to |>=| valueNumericCol)
          List(from, to).flatten.reduceOption(_ and _).getOrElse(sqls"true")
        }.reduce(_ or _)
        sqls"IF($sqlEnum, ${bin.name}, $select)"
      }
      sqls"IF($fieldCol = ${attributeWithDetail.attributeDetail.field}, $attributeBinsConds, $select)"
    }
    //select all numbers from transaction table and convert it to interval
    val selectStage1 = sqls"SELECT ${dataInstanceTable.column.id}, ${getAttributeSelect(dataInstanceTable.column.field("field"))} AS a, ${getSelectCond(dataInstanceTable.column.valueNominal, dataInstanceTable.column.valueNumeric, dataInstanceTable.column.field("field"))} AS nom FROM ${dataInstanceTable.table} WHERE ${dataInstanceTable.column.field("field")} IN (${fieldMap.keys}) AND ${dataInstanceTable.column.valueNumeric} IS NOT NULL"
    //insert all select intervals into preprocessing transaction table
    //each interval is joined with interval from value table; then we use only id as value in transaction table
    sql"""
      INSERT INTO ${preprocessingInstanceTable.table} (${preprocessingInstanceTable.column.columns})
      SELECT s1.id, ${pv.result.attribute}, ${pv.result.id}
      FROM ($selectStage1) s1
      INNER JOIN ${preprocessingValueTable as pv} ON (${pv.attribute} = s1.a AND ${pv.value} = s1.nom)
      WHERE s1.nom IS NOT NULL
      """.execute().apply()
  }

  protected def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail] = {
    //field map for quick access to attribute with detail by field id
    val fieldMap = attributes.map(x => x.attributeDetail.field -> x).toMap
    DBConn autoCommit { implicit session =>
      taskStatusProcessor.newStatus(s"Aggregated values are now populating with indexing...")
      populateValueTable(attributes, fieldMap)
      taskStatusProcessor.newStatus(s"Attribute columns are now populating...")
      populateInstanceTable(attributes, fieldMap)
      //select number of all unique values for each attribute
      val attributeFreqMap = sql"SELECT ${preprocessingValueTable.column.attribute}, COUNT(${preprocessingValueTable.column.id}) AS freq FROM ${preprocessingValueTable.table} GROUP BY ${preprocessingValueTable.column.attribute}"
        .map(wrs => wrs.int(preprocessingValueTable.column.attribute) -> wrs.int("freq")).list().apply().toMap
      //then we save this count into attribute meta information table
      attributes.map(attributeWithDetail => attributeWithDetail.attributeDetail.copy(uniqueValuesSize = attributeFreqMap(attributeWithDetail.attributeDetail.id)))
    }
  }

  override def attributeIsValid(attribute: NumericIntervalsAttribute, fieldDetail: FieldDetail): Boolean = fieldDetail.`type` == NumericFieldType

  override protected def buildWrapper(f: => Seq[AttributeDetail]): Seq[AttributeDetail] = PersistentLock(PersistentLocks.datasetLockName(dataset.id)) {
    f
  }

}

object MysqlNumericIntervalsAttributeBuilder {

  import DatasetTypeConversions.Limited._

  /**
    * Create interval enumeration attribute builder
    *
    * @param attributes          attributes
    * @param datasetDetail       dataset
    * @param mysqlDBConnector    db connector
    * @param taskStatusProcessor status
    * @return attribute builder
    */
  def apply(attributes: Seq[NumericIntervalsAttribute], datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): AttributeBuilder[NumericIntervalsAttribute] = {
    new ValidationAttributeBuilder(
      new MysqlNumericIntervalsAttributeBuilder(datasetDetail, attributes)
    )
  }

}