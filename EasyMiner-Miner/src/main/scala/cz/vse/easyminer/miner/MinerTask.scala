package cz.vse.easyminer.miner

import cz.vse.easyminer.preprocessing.DatasetDetail

case class MinerTask(datasetDetail: DatasetDetail,
                     antecedent: Option[BoolExpression[Attribute]],
                     interestMeasures: InterestMeasures,
                     consequent: Option[BoolExpression[Attribute]])

trait MinerTaskValidator {
  def validate(mt: MinerTask): Unit
}

