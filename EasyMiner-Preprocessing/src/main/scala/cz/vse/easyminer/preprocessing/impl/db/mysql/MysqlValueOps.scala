package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.impl.db.DbValueHistogramOps
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.ValidationValueOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeNumericDetailTable, ValueTable}
import scalikejdbc.{HasExtractor, SQLToList}
import scalikejdbc._

/**
 * Created by propan on 28. 1. 2016.
 */
class MysqlValueOps private[db](val dataset: DatasetDetail, val attribute: AttributeDetail)(implicit connector: MysqlDBConnector) extends ValueOps with DbValueHistogramOps {

  import connector._

  def getValues(offset: Int, limit: Int): Seq[ValueDetail] = DBConn readOnly {
    implicit session =>
      val valueTable = new ValueTable(dataset.id)
      val v = valueTable.syntax("v")
      sql"SELECT ${v.result.*} FROM ${valueTable as v} WHERE ${v.attribute} = $columnId LIMIT $limit OFFSET $offset".map(valueTable(v.resultName, attribute.`type`)).list().apply()
  }

  def getNumericStats: Option[AttributeNumericDetail] = DBConn readOnly {
    implicit session =>
      val n = AttributeNumericDetailTable.syntax("n")
      sql"SELECT ${n.result.*} FROM ${AttributeNumericDetailTable as n} WHERE ${n.id} = $columnId".map(AttributeNumericDetailTable(n.resultName)).first().apply()
  }

  protected[this] def histogramSqlQuery(sql: SQLToList[Bin, HasExtractor]): Seq[Bin] = DBConn readOnly { implicit session =>
    sql.apply()
  }

  protected[this] val columnId: Int = attribute.id
  protected[this] val sqlModuloSymbol: scalikejdbc.SQLSyntax = sqls"MOD"
  protected[this] lazy val valueTableProperties: ValueTableProperties = {
    val valueTable = new ValueTable(dataset.id)
    new ValueTableProperties(valueTable.table, valueTable.column.valueNumeric, valueTable.column.frequency, valueTable.column.attribute)
  }
  protected[this] lazy val columnStats: ColumnStats = getNumericStats match {
    case Some(fnd) => NumericColumnStats(fnd.min, fnd.max)
    case None => NoneColumnStats
  }

}

object MysqlValueOps {

  def apply(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail)(implicit mysqlDBConnector: MysqlDBConnector): ValueOps = new ValidationValueOps(
    new MysqlValueOps(datasetDetail, attributeDetail)
  )

}