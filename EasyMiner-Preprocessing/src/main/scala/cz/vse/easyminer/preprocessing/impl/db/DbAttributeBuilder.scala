package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.{StatusCodeException, TaskStatusProcessor}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.mysql.Tables.FieldNumericDetailTable
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.DbAttributeBuilder.Exceptions.FieldNotFound
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeNumericDetailTable, AttributeTable}
import scalikejdbc._

/**
  * Created by propan on 22. 12. 2015.
  */
trait DbAttributeBuilder extends AttributeBuilder {

  private[db] val mysqlDBConnector: MysqlDBConnector

  private[db] val fieldOps: Option[FieldOps]

  private[db] val attributeOps: AttributeOps

  private[db] val taskStatusProcessor: TaskStatusProcessor

  private[db] val attributeAutoActive = true

  private[db] def buildInstanceColumn(fieldDetail: FieldDetail, attributeDetail: AttributeDetail): AttributeDetail

  private[db] def buildWrapper(f: => AttributeDetail) = f

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

  import mysqlDBConnector._

  private def buildMetaAttribute(fieldDetail: FieldDetail): AttributeDetail = DBConn localTx { implicit session =>
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
    AttributeDetail(attributeId, attribute.name, fieldDetail.id, dataset.id, fieldDetail.`type`.toAttributeType, 0, false)
  }

  final def build: AttributeDetail = buildWrapper {
    fieldOps.flatMap(_.getField(attribute.field).map { fieldDetail =>
      val preparedAttributeDetail = buildMetaAttribute(fieldDetail)
      taskStatusProcessor.newStatus("Attribute meta information have been created. The attribute column building is now in progress...")
      try {
        val finalAttributeDetail = buildInstanceColumn(fieldDetail, preparedAttributeDetail)
        if (attributeAutoActive) activeAttribute(finalAttributeDetail) else finalAttributeDetail
      } catch {
        case th: Throwable =>
          attributeOps.deleteAttribute(preparedAttributeDetail.id)
          throw th
      }
    }).getOrElse(throw new FieldNotFound(attribute))
  }

  def activeAttribute(attributeDetail: AttributeDetail) = {
    DBConn autoCommit { implicit session =>
      sql"UPDATE ${AttributeTable.table} SET ${AttributeTable.column.uniqueValuesSize} = ${attributeDetail.uniqueValuesSize}, ${AttributeTable.column.active} = 1 WHERE ${AttributeTable.column.id} = ${attributeDetail.id}".execute().apply()
    }
    attributeDetail.copy(active = true)
  }

}

object DbAttributeBuilder {

  object Exceptions {

    class FieldNotFound(attribute: Attribute) extends Exception(s"Data field with ID ${attribute.field} does not exist.") with StatusCodeException.BadRequest

    class DataSourceNotFound(dataSourceId: Int) extends Exception(s"DataSource with ID $dataSourceId does not exist.") with StatusCodeException.BadRequest

  }

}