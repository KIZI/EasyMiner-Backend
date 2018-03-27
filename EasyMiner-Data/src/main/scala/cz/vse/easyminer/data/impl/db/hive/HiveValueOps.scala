package cz.vse.easyminer.data.impl.db.hive

import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.hive.Tables.ValueTable
import cz.vse.easyminer.data.impl.db.mysql.MysqlValueOps
import cz.vse.easyminer.data.impl.db.{DbValueHistogramOps, ValidationValueOps}
import scalikejdbc._

/**
 * Created by propan on 8. 12. 2015.
 */
class HiveValueOps private[db](mysqlValueOps: MysqlValueOps)(implicit hiveDBConnector: HiveDBConnector) extends ValueOps with DbValueHistogramOps {

  import hiveDBConnector._

  val dataSource: DataSourceDetail = mysqlValueOps.dataSource

  val field: FieldDetail = mysqlValueOps.field

  protected[this] lazy val valueTableProperties: ValueTableProperties = {
    val valueTable = new ValueTable(dataSource.id)
    new ValueTableProperties(valueTable.table, valueTable.column.valueNumeric, valueTable.column.frequency, valueTable.column.field("field"))
  }

  protected[this] def histogramSqlQuery(sql: SQLToList[Bin, HasExtractor]): Seq[Bin] = DBConn readOnly { implicit session =>
    sql.apply()
  }

  protected[this] val columnId: Int = field.id
  protected[this] val sqlModuloSymbol: scalikejdbc.SQLSyntax = sqls"%"
  protected[this] lazy val columnStats: ColumnStats = getNumericStats match {
    case Some(fnd) => NumericColumnStats(fnd.min, fnd.max)
    case None => NoneColumnStats
  }

  def getNumericStats: Option[FieldNumericDetail] = mysqlValueOps.getNumericStats

  def getValues(offset: Int, limit: Int): Seq[ValueDetail] = DBConn readOnly { implicit session =>
    val valueTable = new ValueTable(dataSource.id)
    val v = valueTable.syntax("v")
    val cursorCondition = field.`type` match {
      case NominalFieldType => sqls"${v.rank} >= ${offset + 1} AND ${v.rank} < ${offset + 1 + limit}"
      case NumericFieldType => sqls"${v.rank} >= ${offset + 1 + field.uniqueValuesSizeNominal - field.uniqueValuesSizeNumeric} AND ${v.rank} < ${offset + 1 + field.uniqueValuesSizeNominal - field.uniqueValuesSizeNumeric + limit}"
    }
    sql"SELECT ${v.result.*} FROM ${valueTable as v} WHERE ${v.field("field")} = ${field.id} AND $cursorCondition".map(valueTable(v.resultName, field.`type`)).list().apply()
  }

}

object HiveValueOps {

  def apply(dataSource: DataSourceDetail, field: FieldDetail)(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector) = new ValidationValueOps(new HiveValueOps(new MysqlValueOps(dataSource, field)))

}