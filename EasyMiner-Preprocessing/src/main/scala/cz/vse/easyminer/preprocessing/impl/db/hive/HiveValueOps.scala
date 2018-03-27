package cz.vse.easyminer.preprocessing.impl.db.hive

import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.ValidationValueOps
import cz.vse.easyminer.preprocessing.impl.db.hive.Tables.ValueTable
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlValueOps
import scalikejdbc._

/**
  * Created by propan on 28. 1. 2016.
  */
class HiveValueOps private(mysqlValueOps: MysqlValueOps)(implicit connector: HiveDBConnector) extends ValueOps {

  import connector._

  val dataset: DatasetDetail = mysqlValueOps.dataset

  val attribute: AttributeDetail = mysqlValueOps.attribute

  def getValues(offset: Int, limit: Int): Seq[ValueDetail] = DBConn readOnly { implicit session =>
    val valueTable = new ValueTable(dataset.id)
    val v = valueTable.syntax("v")
    sql"SELECT ${v.result.*} FROM ${valueTable as v} WHERE ${v.attribute} = ${attribute.id} AND ${v.id} >= ${offset + 1} AND ${v.id} < ${offset + 1 + limit}".map(valueTable(v.resultName)).list().apply()
  }

}

object HiveValueOps {

  def apply(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail)(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector): ValueOps = new ValidationValueOps(
    new HiveValueOps(new MysqlValueOps(datasetDetail, attributeDetail))
  )

}