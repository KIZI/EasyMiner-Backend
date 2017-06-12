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
import cz.vse.easyminer.data.{FieldOps, NominalFieldType, NumericFieldType}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.PersistentLocks
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 10. 11. 2016.
  */

/**
  * Class for creation attribute by user-specified nominal enumeration (mappings to bins)
  *
  * @param dataset             dataset detail
  * @param attributes          input attribute definitions
  * @param mysqlDBConnector    mysql database connections
  * @param taskStatusProcessor task processor for monitoring
  */
class MysqlNominalEnumerationAttributeBuilder private[db](val dataset: DatasetDetail,
                                                          val attributes: Seq[NominalEnumerationAttribute])
                                                         (implicit
                                                          private[db] val mysqlDBConnector: MysqlDBConnector,
                                                          private[db] val taskStatusProcessor: TaskStatusProcessor) extends DbAttributeBuilder[NominalEnumerationAttribute] with DbMysqlTables {

  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.Limited._
  import mysqlDBConnector._

  private[db] val attributeOps: AttributeOps = dataset.toAttributeOps(dataset)

  private[db] val fieldOps: Option[FieldOps] = dataset.toFieldOps

  private[db] def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail] = {
    def getAttributeSelect(fieldCol: SQLSyntax) = attributes.foldLeft(sqls"NULL")((select, attributeWithDetail) => sqls"IF(${dataValueTable.column.field("field")} = ${attributeWithDetail.attributeDetail.field}, ${attributeWithDetail.attributeDetail.id}, $select)")
    def getSelectCond(valueCol: SQLSyntax, fieldCol: SQLSyntax) = attributes.foldLeft(sqls"$valueCol") { (select, attributeWithDetail) =>
      attributeWithDetail.attribute.bins.foldLeft(select) { (select, bin) =>
        val sqlEnum = bin.values.map(x => sqls"$valueCol = $x").reduce(_ or _)
        sqls"IF($fieldCol = ${attributeWithDetail.attributeDetail.field} AND ($sqlEnum), ${bin.name}, $select)"
      }
    }
    def getValueNominalOrNumericWhere(valueNumericCol: SQLSyntax, fieldCol: SQLSyntax) = attributes.iterator.map { attributeWithDetail =>
      val fieldEquality = sqls"$fieldCol = ${attributeWithDetail.fieldDetail.id}"
      attributeWithDetail.fieldDetail.`type` match {
        case NominalFieldType => fieldEquality
        case NumericFieldType => fieldEquality and sqls"$valueNumericCol IS NOT NULL"
      }
    }.reduce(_ or _)
    DBConn autoCommit { implicit session =>
      taskStatusProcessor.newStatus(s"Aggregated values are now populating with indexing...")
      val maxValueId = sql"SELECT MAX(${preprocessingValueTable.column.id}) FROM ${preprocessingValueTable.table}".map(_.intOpt(1)).first().apply().flatten.getOrElse(0)
      val selectStage1 = sqls"SELECT ${getAttributeSelect(dataValueTable.column.field("field"))} AS a, ${getSelectCond(dataValueTable.column.valueNominal, dataValueTable.column.field("field"))} AS nom, ${dataValueTable.column.frequency} AS f FROM ${dataValueTable.table} WHERE ${getValueNominalOrNumericWhere(dataValueTable.column.valueNumeric, dataValueTable.column.field("field"))} ORDER BY ${dataValueTable.column.field("field")}, ${dataValueTable.column.id}"
      val selectStage2 = sqls"SELECT s1.a, s1.nom, SUM(s1.f) FROM ($selectStage1) s1 GROUP BY s1.a, s1.nom"
      sql"INSERT INTO ${preprocessingValueTable.table} (${preprocessingValueTable.column.columns}) SELECT @rownum := @rownum + 1 AS rank, t.* FROM ($selectStage2) t, (SELECT @rownum := $maxValueId) r".execute().apply()
      taskStatusProcessor.newStatus(s"Attribute columns are now populating...")
      val selectStage3 = sqls"SELECT ${dataInstanceTable.column.id}, ${getAttributeSelect(dataInstanceTable.column.field("field"))} AS a, ${getSelectCond(dataInstanceTable.column.valueNominal, dataInstanceTable.column.field("field"))} AS nom FROM ${dataInstanceTable.table} WHERE ${getValueNominalOrNumericWhere(dataInstanceTable.column.valueNumeric, dataInstanceTable.column.field("field"))}"
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

  override private[db] def buildWrapper(f: => Seq[AttributeDetail]): Seq[AttributeDetail] = PersistentLock(PersistentLocks.datasetLockName(dataset.id)) {
    f
  }

}

object MysqlNominalEnumerationAttributeBuilder {

  def apply(attributes: Seq[NominalEnumerationAttribute], datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): AttributeBuilder[NominalEnumerationAttribute] = {
    new ValidationAttributeBuilder(
      new MysqlNominalEnumerationAttributeBuilder(datasetDetail, attributes)
    )
  }

}
