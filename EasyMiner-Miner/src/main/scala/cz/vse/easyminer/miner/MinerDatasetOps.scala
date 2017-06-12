/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import cz.vse.easyminer.preprocessing.{DatasetDetail, Item}
import scalikejdbc.interpolation.SQLSyntax

/**
  * Created by Vaclav Zeman on 23. 2. 2016.
  */

/**
  * Abstraction for dataset operations.
  * Operations read items from transactions.
  */
trait MinerDatasetOps {

  val datasetDetail: DatasetDetail

  /**
    * We select several items by several select queries.
    * Then we intersect all selected items.
    * It is good function if we want to detect items which are on both rule sides (antecedent and consequent)
    *
    * @param selects select queries
    * @return list of intersected items
    */
  def fetchIntersectedItemsBySelects(selects: SQLSyntax*): List[Item]

  /**
    * We select items minus other items (complement)
    * It is for detection of items which appear only on left or right side of a rule
    *
    * @param select      selection query
    * @param minusSelect minus selection query
    * @return result is items fetched by "select" minus items fetched by "minusSelect"
    */
  def fetchComplementedItemsBySelects(select: SQLSyntax, minusSelect: SQLSyntax): List[Item]

  /**
    * Select items from select query
    *
    * @param select select query
    * @return list of selected items
    */
  def fetchItemsBySelect(select: SQLSyntax): List[Item]

}
