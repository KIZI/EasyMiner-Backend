package cz.vse.easyminer.miner

case class ARule(antecedent: Option[BoolExpression[FixedValue]],
                 consequent: BoolExpression[FixedValue],
                 interestMeasures: InterestMeasures,
                 contingencyTable: ContingencyTable)

trait ARuleVisualizer {
  def aruleToString(arule: ARule): String
}