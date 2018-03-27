package cz.vse.easyminer.miner.impl.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.miner.MinerDatasetOps
import cz.vse.easyminer.preprocessing.Item
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.ValueTable
import scalikejdbc._

trait MysqlMinerDatasetOps extends MinerDatasetOps {

  val mysqlDBConnector: MysqlDBConnector
  lazy val valueTable = new ValueTable(datasetDetail.id)

  import mysqlDBConnector._

  private def getBasicItemsQuery(where: SQLSyntax) =
    sqls"""
    SELECT ${valueTable.column.id}, ${valueTable.column.attribute}
    FROM ${valueTable.table}
    WHERE $where
    """

  def fetchIntersectedItemsBySelects(where: SQLSyntax*): List[Item] = DBConn readOnly { implicit session =>
    val innerSelect = where.map(x => getBasicItemsQuery(x)).reduce((s1, s2) => sqls"(($s1) UNION ALL ($s2))")
    sql"SELECT t.${valueTable.column.id}, t.${valueTable.column.attribute} FROM $innerSelect t GROUP BY t.${valueTable.column.id}, t.${valueTable.column.attribute} HAVING COUNT(*) > 1".map { wrs =>
      Item(wrs.int(valueTable.column.attribute), wrs.int(valueTable.column.id))
    }.list().apply()
  }

  def fetchItemsBySelect(where: SQLSyntax): List[Item] = DBConn readOnly { implicit session =>
    sql"${getBasicItemsQuery(where)}".map { wrs =>
      Item(wrs.int(valueTable.column.attribute), wrs.int(valueTable.column.id))
    }.list().apply()
  }

  def fetchComplementedItemsBySelects(where: SQLSyntax, minusWhere: SQLSyntax): List[Item] = DBConn readOnly { implicit session =>
    sql"""SELECT t1.${valueTable.column.id}, t1.${valueTable.column.attribute}
          FROM (${getBasicItemsQuery(where)}) t1
          LEFT JOIN (${getBasicItemsQuery(minusWhere)}) t2 USING (${valueTable.column.id}, ${valueTable.column.attribute})
          WHERE t2.${valueTable.column.id} IS NULL AND t1.${valueTable.column.id} IS NOT NULL"""
      .map { wrs =>
        Item(wrs.int(valueTable.column.attribute), wrs.int(valueTable.column.id))
      }
      .list()
      .apply()
  }

}