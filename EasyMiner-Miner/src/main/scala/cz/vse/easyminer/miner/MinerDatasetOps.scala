package cz.vse.easyminer.miner

import cz.vse.easyminer.preprocessing.{DatasetDetail, Item}
import scalikejdbc.interpolation.SQLSyntax

/**
 * Created by propan on 23. 2. 2016.
 */
trait MinerDatasetOps {

  val datasetDetail: DatasetDetail

  def fetchIntersectedItemsBySelects(selects: SQLSyntax*): List[Item]

  def fetchComplementedItemsBySelects(select: SQLSyntax, minusSelect: SQLSyntax): List[Item]

  def fetchItemsBySelect(select: SQLSyntax): List[Item]

}
