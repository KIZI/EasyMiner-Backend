/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

/**
  * Class which represents association rule
  * It contains left side (antecedent), right side (consequent), interest measures and 4-ft contingency table
  *
  * @param antecedent       left side of the rule
  * @param consequent       right side of the rule
  * @param interestMeasures interest measures (support, confidence etc.)
  * @param contingencyTable 4ft contingency table
  */
case class ARule(antecedent: List[FixedValue],
                 consequent: List[FixedValue],
                 interestMeasures: InterestMeasures,
                 contingencyTable: ContingencyTable)

/**
  * Trait which converts association rule to string
  */
trait ARuleVisualizer {
  def aruleToString(arule: ARule): String
}