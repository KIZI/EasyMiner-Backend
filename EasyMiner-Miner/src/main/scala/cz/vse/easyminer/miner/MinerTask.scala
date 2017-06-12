/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import cz.vse.easyminer.preprocessing.DatasetDetail

/**
  * Task definition for association rules mining
  *
  * @param datasetDetail    dataset detail
  * @param antecedent       left side constraint of rules
  * @param interestMeasures interest measures thresholds
  * @param consequent       right side constraint of rules
  */
case class MinerTask(datasetDetail: DatasetDetail,
                     antecedent: Option[BoolExpression[Attribute]],
                     interestMeasures: InterestMeasures,
                     consequent: Option[BoolExpression[Attribute]])

trait MinerTaskValidator {

  /**
    * Validate task definition whether it has valid parameters
    *
    * @param mt miner task
    */
  def validate(mt: MinerTask): Unit

}

