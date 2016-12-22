package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.CollectionValidators
import cz.vse.easyminer.core.{TaskStatusProcessor, Validator}
import cz.vse.easyminer.data
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.mysql.Tables.{FieldNumericDetailTable, InstanceTable => MysqlDataInstanceTable, ValueTable => MysqlDataValueTable}
import cz.vse.easyminer.preprocessing.NumericIntervalsAttribute.Interval
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeNumericDetailTable, AttributeTable, InstanceTable => MysqlPreprocessingInstanceTable, ValueTable => MysqlPreprocessingValueTable}
import scalikejdbc._

import scala.language.implicitConversions
import scala.util.Try

/**
  * Created by propan on 22. 12. 2015.
  */
trait DbAttributeBuilder[T <: Attribute] extends AttributeBuilder[T] {

  case class AttributeWithDetail(attribute: T, attributeDetail: AttributeDetail, fieldDetail: FieldDetail)

  private[db] val mysqlDBConnector: MysqlDBConnector

  private[db] val fieldOps: Option[FieldOps]

  private[db] val attributeOps: AttributeOps

  private[db] val taskStatusProcessor: TaskStatusProcessor

  private[db] val attributeAutoActive = true

  private[db] def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail]

  private[db] def buildWrapper(f: => Seq[AttributeDetail]) = f

  private[db] def getNumericComparator(intervalBorder: IntervalBorder, greaterThan: Boolean) = intervalBorder match {
    case _: InclusiveIntervalBorder if greaterThan => sqls">="
    case _: InclusiveIntervalBorder if !greaterThan => sqls"<="
    case _: ExclusiveIntervalBorder if greaterThan => sqls">"
    case _: ExclusiveIntervalBorder if !greaterThan => sqls"<"
  }

  implicit private class FieldTypeConverter(fieldType: FieldType) {

    def toAttributeType = fieldType match {
      case NominalFieldType => NominalAttributeType
      case NumericFieldType => NumericAttributeType
    }

    def toSqlString = fieldType match {
      case NominalFieldType => "NOMINAL"
      case NumericFieldType => "NUMERIC"
    }

  }

  implicit private class AttributeTypeConverter(attributeType: AttributeType) extends FieldTypeConverter(attributeType)

  import mysqlDBConnector._

  implicit private def attributeTypeToFieldType(attributeType: AttributeType): FieldType = attributeType match {
    case NominalAttributeType => NominalFieldType
    case NumericAttributeType => NumericFieldType
  }

  private def buildMetaAttribute(fieldDetail: FieldDetail, attribute: Attribute): AttributeDetail = DBConn localTx { implicit session =>
    val attributeId =
      sql"""INSERT INTO ${AttributeTable.table}
            (${AttributeTable.column.name}, ${AttributeTable.column.field("field")}, ${AttributeTable.column.dataset}, ${AttributeTable.column.`type`}, ${AttributeTable.column.uniqueValuesSize})
            VALUES
            (${attribute.name}, ${fieldDetail.id}, ${dataset.id}, ${fieldDetail.`type`.toSqlString}, 0)""".updateAndReturnGeneratedKey().apply().toInt
    val statsColumns = FieldNumericDetailTable.column.columns.filter(_ != FieldNumericDetailTable.column.id)
    sql"""INSERT INTO ${AttributeNumericDetailTable.table}
          (${AttributeNumericDetailTable.column.columns})
          SELECT $attributeId, $statsColumns FROM ${FieldNumericDetailTable.table}
          WHERE ${FieldNumericDetailTable.column.id} = ${fieldDetail.id}""".execute().apply()
    AttributeDetail(attributeId, attribute.name, fieldDetail.id, dataset.id, fieldDetail.`type`.toAttributeType, fieldDetail.uniqueValuesSize, false)
  }

  final def build: Seq[AttributeDetail] = {
    Validator(attributes)(CollectionValidators.NonEmpty)
    taskStatusProcessor.newStatus("Attribute builder is waiting for completion of the last task which may still be in progress...")
    buildWrapper {
      taskStatusProcessor.newStatus("This attribute building task is now in progress...")
      val preparedAttributeDetail = fieldOps.map { fieldOps =>
        rollbackIfFailure(attributes.flatMap { attribute =>
          fieldOps.getField(attribute.field).flatMap { fieldDetail =>
            if (attributeIsValid(attribute, fieldDetail)) Some(Try(AttributeWithDetail(attribute, buildMetaAttribute(fieldDetail, attribute), fieldDetail))) else None
          }
        })(attibute => attributeOps.deleteAttribute(attibute.attributeDetail.id))
      }.getOrElse(Nil)
      taskStatusProcessor.newStatus("Attributes meta information have been created. Attributes data are now processing...")
      try {
        Validator(preparedAttributeDetail)(CollectionValidators.NonEmpty)
        val finalAttributes = buildAttributes(preparedAttributeDetail)
        if (attributeAutoActive) finalAttributes.map(activeAttribute) else finalAttributes
      } catch {
        case th: Throwable =>
          preparedAttributeDetail.map(_.attributeDetail.id).foreach(attributeOps.deleteAttribute)
          throw th
      }
    }
  }

  def attributeIsValid(attribute: T, fieldDetail: FieldDetail) = true

  def activeAttribute(attributeDetail: AttributeDetail) = {
    DBConn autoCommit { implicit session =>
      sql"UPDATE ${AttributeTable.table} SET ${AttributeTable.column.uniqueValuesSize} = ${attributeDetail.uniqueValuesSize}, ${AttributeTable.column.active} = 1, ${AttributeTable.column.`type`} = ${attributeDetail.`type`.toSqlString} WHERE ${AttributeTable.column.id} = ${attributeDetail.id}".execute().apply()
    }
    attributeDetail.copy(active = true)
  }

}

trait DbMysqlTables {
  val dataset: DatasetDetail
  protected[this] val preprocessingInstanceTable = new MysqlPreprocessingInstanceTable(dataset.id)
  protected[this] val dataInstanceTable = new MysqlDataInstanceTable(dataset.dataSource)
  protected[this] val preprocessingValueTable = new MysqlPreprocessingValueTable(dataset.id)
  protected[this] val dataValueTable = new MysqlDataValueTable(dataset.dataSource)
  protected[this] val dataFieldNumericDetailTable = FieldNumericDetailTable
  protected[this] val di = dataInstanceTable.syntax("di")
  protected[this] val pv = preprocessingValueTable.syntax("pv")
}

trait IntervalOps {

  case class AttributeInterval(interval: Interval, frequency: Int)

  @scala.annotation.tailrec
  final def smoothAttributeIntervals(intervals: collection.mutable.ArrayBuffer[AttributeInterval], records: => Traversable[data.NumericValueDetail])
                                    (canItMoveLeft: (data.NumericValueDetail, AttributeInterval, AttributeInterval) => Boolean)
                                    (canItMoveRight: (data.NumericValueDetail, AttributeInterval, AttributeInterval) => Boolean): Unit = {
    val (_, _, isChanged) = records.foldLeft((Option.empty[data.NumericValueDetail], intervals.length - 1, false)) { case ((lastValue, pointer, isChanged), valueNumeric) =>
      if (pointer > 0) {
        val currentInterval = intervals(pointer)
        val prevInterval = intervals(pointer - 1)
        if (currentInterval.frequency > prevInterval.frequency && valueNumeric.value == currentInterval.interval.from.value && lastValue.nonEmpty) {
          if (canItMoveLeft(valueNumeric, prevInterval, currentInterval)) {
            intervals.update(pointer - 1, AttributeInterval(prevInterval.interval.copy(to = currentInterval.interval.from), prevInterval.frequency + valueNumeric.frequency))
            intervals.update(pointer, AttributeInterval(currentInterval.interval.copy(from = InclusiveIntervalBorder(lastValue.get.value)), currentInterval.frequency - valueNumeric.frequency))
            (None, pointer - 1, true)
          } else {
            (None, pointer - 1, isChanged)
          }
        } else if (currentInterval.frequency < prevInterval.frequency && lastValue.exists(_.value == prevInterval.interval.to.value)) {
          val nextLastValue = if (prevInterval.interval.from.value == valueNumeric.value) None else Some(valueNumeric)
          if (canItMoveRight(lastValue.get, prevInterval, currentInterval)) {
            intervals.update(pointer - 1, AttributeInterval(prevInterval.interval.copy(to = InclusiveIntervalBorder(valueNumeric.value)), prevInterval.frequency - lastValue.get.frequency))
            intervals.update(pointer, AttributeInterval(currentInterval.interval.copy(from = prevInterval.interval.to), currentInterval.frequency + lastValue.get.frequency))
            (nextLastValue, pointer - 1, true)
          } else {
            (nextLastValue, pointer - 1, isChanged)
          }
        } else if (currentInterval.frequency == prevInterval.frequency && valueNumeric.value == currentInterval.interval.from.value) {
          //intervals are equal -> no moves
          (None, pointer - 1, isChanged)
        } else if (valueNumeric.value == prevInterval.interval.from.value) {
          //it is the last cutpoint within a current window, we need to change pointer to the next interval
          (None, pointer - 1, isChanged)
        } else {
          //no cutpoint -> go to the next value and safe the current as a last value
          (Some(valueNumeric), pointer, isChanged)
        }
      } else {
        //only one interval remains -> no moves
        (None, pointer, isChanged)
      }
    }
    if (isChanged) smoothAttributeIntervals(intervals, records)(canItMoveLeft)(canItMoveRight)
  }

}