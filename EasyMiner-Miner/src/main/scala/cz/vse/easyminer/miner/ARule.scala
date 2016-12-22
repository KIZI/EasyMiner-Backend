package cz.vse.easyminer.miner

case class ARule(antecedent: List[FixedValue],
                 consequent: List[FixedValue],
                 interestMeasures: InterestMeasures,
                 contingencyTable: ContingencyTable)

trait ARuleVisualizer {
  def aruleToString(arule: ARule): String
}