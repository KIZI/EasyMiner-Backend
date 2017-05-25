/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import cz.vse.easyminer.preprocessing.DatasetDetail

case class MinerTask(datasetDetail: DatasetDetail,
                     antecedent: Option[BoolExpression[Attribute]],
                     interestMeasures: InterestMeasures,
                     consequent: Option[BoolExpression[Attribute]])

trait MinerTaskValidator {
  def validate(mt: MinerTask): Unit
}

