/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.miner.MinerDatasetOps
import cz.vse.easyminer.preprocessing.Item
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.ValueTable
import scalikejdbc._

/**
  * Mysql dataset operations.
  * Operations read items from transactions.
  */
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

  /**
    * We select several items by several select queries.
    * Then we intersect all selected items.
    * It is good function if we want to detect items which are on both rule sides (antecedent and consequent)
    *
    * @param where select queries
    * @return list of intersected items
    */
  def fetchIntersectedItemsBySelects(where: SQLSyntax*): List[Item] = DBConn readOnly { implicit session =>
    val innerSelect = where.map(x => getBasicItemsQuery(x)).reduce((s1, s2) => sqls"(($s1) UNION ALL ($s2))")
    sql"SELECT t.${valueTable.column.id}, t.${valueTable.column.attribute} FROM $innerSelect t GROUP BY t.${valueTable.column.id}, t.${valueTable.column.attribute} HAVING COUNT(*) > 1".map { wrs =>
      Item(wrs.int(valueTable.column.attribute), wrs.int(valueTable.column.id))
    }.list().apply()
  }


  /**
    * Select items from select query
    *
    * @param where select query
    * @return list of selected items
    */
  def fetchItemsBySelect(where: SQLSyntax): List[Item] = DBConn readOnly { implicit session =>
    sql"${getBasicItemsQuery(where)}".map { wrs =>
      Item(wrs.int(valueTable.column.attribute), wrs.int(valueTable.column.id))
    }.list().apply()
  }

  /**
    * We select items minus other items (complement)
    * It is for detection of items which appear only on left or right side of a rule
    *
    * @param where      selection query
    * @param minusWhere minus selection query
    * @return result is items fetched by "select" minus items fetched by "minusSelect"
    */
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