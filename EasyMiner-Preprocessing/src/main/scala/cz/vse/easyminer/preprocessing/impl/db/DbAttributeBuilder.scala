/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.CollectionValidators
import cz.vse.easyminer.core.{TaskStatusProcessor, Validator}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.hive.Tables.{InstanceTable => HiveDataInstanceTable, ValueTable => HiveDataValueTable}
import cz.vse.easyminer.data.impl.db.mysql.Tables.{FieldNumericDetailTable, InstanceTable => MysqlDataInstanceTable, ValueTable => MysqlDataValueTable}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.hive.Tables.{InstanceTable => HivePreprocessingInstanceTable, ValueTable => HivePreprocessingValueTable}
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeTable, InstanceTable => MysqlPreprocessingInstanceTable, ValueTable => MysqlPreprocessingValueTable}
import scalikejdbc._

import scala.language.implicitConversions
import scala.util.Try

/**
  * Created by Vaclav Zeman on 22. 12. 2015.
  */

/**
  * Main abstraction for attribute building from fields
  * It automatically validates input attributes, creates empty attributes, launches attribute builders and completes finally information about dataset.
  * It also controls any errors and executes rollback if any exception
  *
  * @tparam T type of attribute preprocessings
  */
trait DbAttributeBuilder[T <: Attribute] extends AttributeBuilder[T] {

  case class AttributeWithDetail(attribute: T, attributeDetail: AttributeDetail, fieldDetail: FieldDetail)

  /**
    * Mysql database connection
    */
  protected val mysqlDBConnector: MysqlDBConnector

  /**
    * Field operations of a data source
    */
  protected val fieldOps: Option[FieldOps]

  /**
    * Attribute operations of a dataset
    */
  protected val attributeOps: AttributeOps

  /**
    * Task processor for monitoring of building process
    */
  protected val taskStatusProcessor: TaskStatusProcessor

  /**
    * If this flag is true then it activates created attributes automatically after buildAttributes function
    */
  protected val attributeAutoActive = true

  /**
    * This is main building function which populates attributes from fields and their field detail and attribute detail
    *
    * @param attributes input attribute definitions with created empty attribute detail and field detail
    * @return populated attribute details
    */
  protected def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail]

  /**
    * This function wraps building process.
    * It is suitable if we want to lock some tables before building and then release them after successful building or for controlling building process
    *
    * @param f function which we can wrap/decorate
    * @return decorated function
    */
  protected def buildWrapper(f: => Seq[AttributeDetail]) = f

  import mysqlDBConnector._

  private def buildMetaAttribute(fieldDetail: FieldDetail, attribute: Attribute): AttributeDetail = DBConn localTx { implicit session =>
    val attributeId =
      sql"""INSERT INTO ${AttributeTable.table}
            (${AttributeTable.column.name}, ${AttributeTable.column.field("field")}, ${AttributeTable.column.dataset}, ${AttributeTable.column.uniqueValuesSize})
            VALUES
            (${attribute.name}, ${fieldDetail.id}, ${dataset.id}, 0)""".updateAndReturnGeneratedKey().apply().toInt
    AttributeDetail(attributeId, attribute.name, fieldDetail.id, dataset.id, fieldDetail.uniqueValuesSize, false)
  }

  /**
    * Main building function which calls all necessary methods for creating attributes
    * It controls whole building process
    *
    * @return created attributes
    */
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

  /**
    * Function which can be overwritten.
    * It checks whether input attribute is valid with combination of a field detail
    *
    * @param attribute   attribute to build
    * @param fieldDetail existed field detail which is bound with this attribute
    * @return true if attribute is valid
    */
  def attributeIsValid(attribute: T, fieldDetail: FieldDetail) = true

  /**
    * Function which activates created attribute after building process
    *
    * @param attributeDetail built attribute detail
    * @return activated attribute detail
    */
  def activeAttribute(attributeDetail: AttributeDetail) = {
    DBConn autoCommit { implicit session =>
      sql"UPDATE ${AttributeTable.table} SET ${AttributeTable.column.uniqueValuesSize} = ${attributeDetail.uniqueValuesSize}, ${AttributeTable.column.active} = 1 WHERE ${AttributeTable.column.id} = ${attributeDetail.id}".execute().apply()
    }
    attributeDetail.copy(active = true)
  }

}

/**
  * Definitions of all tables needed for attribute building
  */
trait DbMysqlTables {
  val dataset: DatasetDetail
  protected val preprocessingInstanceTable = new MysqlPreprocessingInstanceTable(dataset.id)
  protected val dataInstanceTable = new MysqlDataInstanceTable(dataset.dataSource)
  protected val preprocessingValueTable = new MysqlPreprocessingValueTable(dataset.id)
  protected val dataValueTable = new MysqlDataValueTable(dataset.dataSource)
  protected val dataFieldNumericDetailTable = FieldNumericDetailTable
  protected val di = dataInstanceTable.syntax("di")
  protected val pv = preprocessingValueTable.syntax("pv")
}

trait DbHiveTables {
  val dataset: DatasetDetail
  protected val preprocessingInstanceTable = new HivePreprocessingInstanceTable(dataset.id)
  protected val dataInstanceTable = new HiveDataInstanceTable(dataset.dataSource)
  protected val preprocessingValueTable = new HivePreprocessingValueTable(dataset.id)
  protected val dataValueTable = new HiveDataValueTable(dataset.dataSource)
  protected val di = dataInstanceTable.syntax("di")
  protected val pv = preprocessingValueTable.syntax("pv")
}