package cz.vse.easyminer
package preprocessing.impl.db
package hive

import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.core.{PersistentLock, TaskStatusProcessor}
import cz.vse.easyminer.data.{NominalFieldType, NumericFieldType}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.PersistentLocks
import scalikejdbc._

/**
  * Created by propan on 13. 3. 2016.
  */
class HiveSimpleAttributeBuilder private(val dataset: DatasetDetail,
                                         val attributes: Seq[SimpleAttribute])
                                        (implicit
                                         protected val mysqlDBConnector: MysqlDBConnector,
                                         hiveDBConnector: HiveDBConnector,
                                         protected val taskStatusProcessor: TaskStatusProcessor) extends DbAttributeBuilder[SimpleAttribute] with DbHiveTables {

  import hiveDBConnector._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.Unlimited._

  protected lazy val attributeOps = dataset.toAttributeOps(dataset)

  protected lazy val fieldOps = dataset.toFieldOps

  private def populateValueTable(attributeWithDetails: Seq[AttributeWithDetail]): Unit = {
    //sql syntaxes
    val valueNominalOrNumericWhere = attributeWithDetails.iterator.map { attributeWithDetail =>
      val fieldEquality = sqls"${dataValueTable.column.field("field")} = ${attributeWithDetail.fieldDetail.id}"
      attributeWithDetail.fieldDetail.`type` match {
        case NominalFieldType => fieldEquality
        case NumericFieldType => fieldEquality and sqls"${dataValueTable.column.valueNumeric} IS NOT NULL"
      }
    }.reduce(_ or _)
    val selectAttributeId = attributeWithDetails.foldLeft(sqls"NULL") { (subcond, attributeWithDetail) =>
      sqls"IF(${dataValueTable.column.field("field")} = ${attributeWithDetail.fieldDetail.id}, ${attributeWithDetail.attributeDetail.id}, $subcond)"
    }
    DBConn autoCommit { implicit session =>
      sql"SET hive.exec.dynamic.partition=true".execute().apply()
      sql"SET hive.exec.dynamic.partition.mode=nonstrict".execute().apply()
      taskStatusProcessor.newStatus(s"Aggregated values are copying and indexing...")
      sql"""
      INSERT INTO TABLE ${preprocessingValueTable.table}
      PARTITION (${preprocessingValueTable.column.attribute})
      SELECT ${dataValueTable.column.rank}, ${dataValueTable.column.valueNominal}, ${dataValueTable.column.frequency}, $selectAttributeId
      FROM ${dataValueTable.table} WHERE $valueNominalOrNumericWhere
      """.execute().apply()
    }
  }

  private def populateInstanceTable(attributeDetails: Seq[AttributeDetail]) = {
    val conditions = attributeDetails.map { attributeDetail =>
      sqls"${pv.attribute} = ${attributeDetail.id} AND ${di.field("field")} = ${attributeDetail.field}"
    }.reduce(_ or _)
    DBConn autoCommit { implicit session =>
      sql"SET hive.exec.dynamic.partition=true".execute().apply()
      sql"SET hive.exec.dynamic.partition.mode=nonstrict".execute().apply()
      sql"SET hive.auto.convert.join=false".execute().apply()
      taskStatusProcessor.newStatus(s"The dataset is now populating... Number of attributes: ${attributeDetails.size}")
      sql"""
      INSERT INTO TABLE ${preprocessingInstanceTable.table}
      PARTITION (${preprocessingInstanceTable.column.attribute})
      SELECT ${di.id}, ${pv.id}, ${pv.attribute}
      FROM ${dataInstanceTable as di}
      JOIN ${preprocessingValueTable as pv} ON (${di.valueNominal} = ${pv.value})
      WHERE $conditions
      DISTRIBUTE BY ${pv.attribute}
      SORT BY ${pv.attribute}, ${di.id}
      """.execute().apply()
    }
  }

  override protected def buildWrapper(f: => Seq[AttributeDetail]): Seq[AttributeDetail] = PersistentLock(PersistentLocks.datasetLockName(dataset.id)) {
    f
  }

  protected def buildAttributes(attributes: Seq[AttributeWithDetail]): Seq[AttributeDetail] = {
    val attributeDetails = attributes.map(_.attributeDetail)
    populateValueTable(attributes)
    populateInstanceTable(attributeDetails)
    attributeDetails
  }

}

object HiveSimpleAttributeBuilder {

  import DatasetTypeConversions.Unlimited._

  def apply(attributes: Seq[SimpleAttribute], datasetDetail: DatasetDetail)
           (implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector, taskStatusProcessor: TaskStatusProcessor): AttributeBuilder[SimpleAttribute] = {
    new ValidationAttributeBuilder[SimpleAttribute](
      new HiveSimpleAttributeBuilder(datasetDetail, attributes)
    )
  }

}