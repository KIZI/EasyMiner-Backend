/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.ValidationValueOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.ValueTable
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 28. 1. 2016.
  */
class MysqlValueOps private[db](val dataset: DatasetDetail, val attribute: AttributeDetail)(implicit connector: MysqlDBConnector) extends ValueOps {

  import connector._

  def getValues(offset: Int, limit: Int): Seq[ValueDetail] = DBConn readOnly {
    implicit session =>
      val valueTable = new ValueTable(dataset.id)
      val v = valueTable.syntax("v")
      sql"SELECT ${v.result.*} FROM ${valueTable as v} WHERE ${v.attribute} = ${attribute.id} ORDER BY ${v.id} LIMIT $limit OFFSET $offset".map(valueTable(v.resultName)).list().apply()
  }

}

object MysqlValueOps {

  def apply(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail)(implicit mysqlDBConnector: MysqlDBConnector): ValueOps = new ValidationValueOps(
    new MysqlValueOps(datasetDetail, attributeDetail)
  )

}