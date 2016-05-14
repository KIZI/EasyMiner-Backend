package cz.vse.easyminer.miner

import cz.vse.easyminer.preprocessing.{DatasetDetail, AttributeDetail}
import scalikejdbc.interpolation.SQLSyntax

/**
 * Created by propan on 23. 2. 2016.
 */
trait MinerDatasetOps {

  val datasetDetail: DatasetDetail

  def fetchIntersectedValuesBySelectsAndAttribute(attributeDetail: AttributeDetail, selects: SQLSyntax*): List[Int]

  def fetchComplementedValuesBySelectsAndAttribute(attributeDetail: AttributeDetail, select: SQLSyntax, minusSelect: SQLSyntax): List[Int]

  def fetchValuesBySelectAndAttribute(attributeDetail: AttributeDetail, select: SQLSyntax): List[Int]

}
