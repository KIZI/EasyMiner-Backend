package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.impl.db.mysql.Tables.{InstanceTable => DataInstanceTable, ValueTable => DataValueTable}
import cz.vse.easyminer.data.impl.db.mysql.{MysqlDataSourceOps, MysqlFieldOps}
import cz.vse.easyminer.data.{FieldDetail, FieldOps}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{InstanceTable => PreprocessingInstanceTable, ValueTable => PreprocessingValueTable}
import cz.vse.easyminer.preprocessing.impl.db.{DbAttributeBuilder, ValidationAttributeBuilder}
import scalikejdbc._

import scala.util.Random

/**
 * Created by propan on 22. 12. 2015.
 */
class MysqlSimpleAttributeBuilder private[db](val attribute: SimpleAttribute,
                                              private[db] val attributeOps: AttributeOps,
                                              private[db] val fieldOps: Option[FieldOps])
                                             (implicit
                                              private[db] val mysqlDBConnector: MysqlDBConnector,
                                              private[db] val taskStatusProcessor: TaskStatusProcessor) extends DbAttributeBuilder {

  import mysqlDBConnector._

  val dataset: DatasetDetail = attributeOps.dataset

  private[db] def buildInstanceColumn(fieldDetail: FieldDetail, attributeDetail: AttributeDetail): AttributeDetail = {
    val preprocessingInstanceTable = new PreprocessingInstanceTable(dataset.id, List(attributeDetail.id))
    val dataInstanceTable = new DataInstanceTable(dataset.dataSource, List(fieldDetail.id))
    val preprocessingValueTable = new PreprocessingValueTable(dataset.id)
    val dataValueTable = new DataValueTable(dataset.dataSource)
    DBConn autoCommit { implicit session =>
      sql"ALTER TABLE ${preprocessingInstanceTable.table} ADD ${preprocessingInstanceTable.columnById(attributeDetail.id)} int(10) unsigned DEFAULT NULL".execute().apply()
    }
    DBConn localTx { implicit session =>
      taskStatusProcessor.newStatus(s"Attribute ${attributeDetail.name}: aggregated values are now populating with indexing...")
      val maxValueId = sql"SELECT MAX(${preprocessingValueTable.column.id}) FROM ${preprocessingValueTable.table} FOR UPDATE".map(_.intOpt(1)).first().apply().flatten.getOrElse(0)
      val selectValues = sqls"SELECT ${dataValueTable.column.valueNominal}, ${dataValueTable.column.valueNumeric}, ${dataValueTable.column.frequency} FROM ${dataValueTable.table} WHERE ${dataValueTable.column.field("field")} = ${fieldDetail.id}"
      val selectAggValues = sqls"SELECT @rownum := @rownum + 1 AS rank, ${attributeDetail.id}, t.* FROM ($selectValues) t, (SELECT @rownum := $maxValueId) r"
      sql"INSERT INTO ${preprocessingValueTable.table} (${preprocessingValueTable.column.columns}) $selectAggValues".execute().apply()
      val pi = preprocessingInstanceTable.syntax("pi")
      val di = dataInstanceTable.syntax("di")
      val pv = preprocessingValueTable.syntax("pv")
      val valueColName = attributeDetail.`type` match {
        case NominalAttributeType => pv.valueNominal
        case NumericAttributeType => pv.valueNumeric
      }
      taskStatusProcessor.newStatus(s"Attribute ${attributeDetail.name}: the dataset attribute column is now populating...")
      sql"""UPDATE ${preprocessingInstanceTable as pi}
            INNER JOIN ${dataInstanceTable as di} ON (${pi.id} = ${di.id})
            INNER JOIN ${preprocessingValueTable as pv} ON (${pv.attribute} = ${attributeDetail.id} AND ${di.column(dataInstanceTable.columnById(fieldDetail.id).value)} = $valueColName)
            SET ${pi.column(preprocessingInstanceTable.columnById(attributeDetail.id).value)} = ${pv.id}
            WHERE $valueColName IS NOT NULL""".execute().apply()
    }
    attributeDetail.copy(uniqueValuesSize = fieldDetail.uniqueValuesSize)
  }

}

object MysqlSimpleAttributeBuilder {

  def apply(attribute: SimpleAttribute, datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): AttributeBuilder = {
    val fieldOps = MysqlDataSourceOps().getDataSource(datasetDetail.dataSource) map MysqlFieldOps.apply
    new ValidationAttributeBuilder(
      new MysqlSimpleAttributeBuilder(attribute, MysqlAttributeOps(datasetDetail), fieldOps)
    )
  }

}