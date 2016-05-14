package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.miner.MinerDatasetOps
import cz.vse.easyminer.preprocessing.AttributeDetail
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.ValueTable
import scalikejdbc._

trait MysqlMinerDatasetOps extends MinerDatasetOps {

  val mysqlDBConnector: MysqlDBConnector
  lazy val valueTable = new ValueTable(datasetDetail.id)

  import mysqlDBConnector._

  private def getBasicValueQuery(attributeDetail: AttributeDetail, select: SQLSyntax) =
    sqls"""
    SELECT DISTINCT $select
    FROM ${valueTable.table}
    WHERE ${valueTable.column.attribute} = ${attributeDetail.id} AND (${valueTable.column.valueNominal} IS NOT NULL OR ${valueTable.column.valueNumeric} IS NOT NULL)
    """

  def fetchIntersectedValuesBySelectsAndAttribute(attributeDetail: AttributeDetail, selects: SQLSyntax*): List[Int] = DBConn readOnly { implicit session =>
    val innerSelect = selects.map(x => getBasicValueQuery(attributeDetail, x)).reduce((s1, s2) => sqls"(($s1) UNION ALL ($s2))")
    sql"SELECT t.${valueTable.column.id} FROM $innerSelect t WHERE t.${valueTable.column.id} IS NOT NULL GROUP BY t.${valueTable.column.id} HAVING COUNT(*) > 1".map(_.int(valueTable.column.id)).list().apply()
  }

  def fetchValuesBySelectAndAttribute(attributeDetail: AttributeDetail, select: SQLSyntax): List[Int] = DBConn readOnly { implicit session =>
    sql"SELECT t.* FROM (${getBasicValueQuery(attributeDetail, select)}) AS t WHERE t.${valueTable.column.id} IS NOT NULL".map(_.int(valueTable.column.id)).list().apply()
  }

  def fetchComplementedValuesBySelectsAndAttribute(attributeDetail: AttributeDetail, select: SQLSyntax, minusSelect: SQLSyntax): List[Int] = DBConn readOnly { implicit session =>
    sql"""SELECT t1.${valueTable.column.id}
          FROM (${getBasicValueQuery(attributeDetail, select)}) t1
          LEFT JOIN (${getBasicValueQuery(attributeDetail, minusSelect)}) t2 USING (${valueTable.column.id})
          WHERE t2.${valueTable.column.id} IS NULL AND t1.${valueTable.column.id} IS NOT NULL"""
      .map(_.int(valueTable.column.id))
      .list()
      .apply()
  }

}