/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.mysql.Tables.{FieldNumericDetailTable, ValueTable}
import cz.vse.easyminer.data.impl.db.{DbValueHistogramOps, ValidationValueOps}
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 1. 9. 2015.
  */

/**
  * Implementation of value operations on mysql database
  *
  * @param dataSource data source
  * @param field      field
  * @param connector  implicit! mysql db connector
  */
class MysqlValueOps private[db](val dataSource: DataSourceDetail, val field: FieldDetail)(implicit connector: MysqlDBConnector) extends ValueOps with DbValueHistogramOps {

  import connector._

  protected[this] val sqlModuloSymbol: scalikejdbc.SQLSyntax = sqls"MOD"
  protected[this] val columnId: Int = field.id
  protected[this] lazy val valueTableProperties: ValueTableProperties = {
    val valueTable = new ValueTable(dataSource.id)
    new ValueTableProperties(valueTable.table, valueTable.column.valueNumeric, valueTable.column.frequency, valueTable.column.field("field"))
  }
  protected[this] lazy val columnStats: ColumnStats = getNumericStats match {
    case Some(fnd) => NumericColumnStats(fnd.min, fnd.max)
    case None => NoneColumnStats
  }

  protected[this] def histogramSqlQuery(sql: SQLToList[Bin, HasExtractor]): Seq[Bin] = DBConn readOnly { implicit session =>
    sql.apply()
  }


  /**
    * Get numeric stats of this field
    *
    * @return stats object or None if the field is not numeric
    */
  def getNumericStats: Option[FieldNumericDetail] = if (field.`type` == NumericFieldType) {
    DBConn readOnly {
      implicit session =>
        val n = FieldNumericDetailTable.syntax("n")
        sql"SELECT ${n.result.*} FROM ${FieldNumericDetailTable as n} WHERE ${n.id} = $columnId".map(FieldNumericDetailTable(n.resultName)).first().apply()
    }
  } else {
    None
  }

  /**
    * Get all values for the data source and field
    *
    * @param offset first record is 0 (not 1)
    * @param limit  it is restricted by the maximal limit value
    * @return seq of value details
    */
  def getValues(offset: Int, limit: Int): Seq[ValueDetail] = DBConn readOnly {
    implicit session =>
      val valueTable = new ValueTable(dataSource.id)
      val v = valueTable.syntax("v")
      val condition = (sqls"${v.field("field")} = $columnId" :: (if (field.`type` == NumericFieldType) List(sqls"${v.valueNumeric} IS NOT NULL") else Nil)).reduce(_ and _)
      sql"SELECT ${v.result.*} FROM ${valueTable as v} WHERE $condition LIMIT $limit OFFSET $offset".map(valueTable(v.resultName, field.`type`)).list().apply()
  }

}

object MysqlValueOps {

  /**
    * Create value operations instance decorated by validator
    *
    * @param dataSource data source
    * @param field      field
    * @param connector  implicit! mysql db connector
    * @return value operations instance
    */
  def apply(dataSource: DataSourceDetail, field: FieldDetail)(implicit connector: MysqlDBConnector) = new ValidationValueOps(new MysqlValueOps(dataSource, field))

}