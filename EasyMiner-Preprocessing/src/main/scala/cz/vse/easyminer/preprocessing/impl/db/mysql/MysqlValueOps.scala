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

/**
  * Implementation of value operations on mysql database
  *
  * @param dataset   dataset detail
  * @param attribute attribute detail
  * @param connector mysql connection
  */
class MysqlValueOps private[db](val dataset: DatasetDetail, val attribute: AttributeDetail)(implicit connector: MysqlDBConnector) extends ValueOps {

  import connector._

  /**
    * Get all values for a specific dataset and attribute
    *
    * @param offset start pointer
    * @param limit  number of records to retrieve
    * @return value details
    */
  def getValues(offset: Int, limit: Int): Seq[ValueDetail] = DBConn readOnly {
    implicit session =>
      val valueTable = new ValueTable(dataset.id)
      val v = valueTable.syntax("v")
      sql"SELECT ${v.result.*} FROM ${valueTable as v} WHERE ${v.attribute} = ${attribute.id} ORDER BY ${v.id} LIMIT $limit OFFSET $offset".map(valueTable(v.resultName)).list().apply()
  }

}

object MysqlValueOps {

  /**
    * Create value operations instance decorated by validator
    *
    * @param datasetDetail    dataset detail
    * @param attributeDetail  attribute detail
    * @param mysqlDBConnector mysql connection
    * @return value operations instance
    */
  def apply(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail)(implicit mysqlDBConnector: MysqlDBConnector): ValueOps = new ValidationValueOps(
    new MysqlValueOps(datasetDetail, attributeDetail)
  )

}