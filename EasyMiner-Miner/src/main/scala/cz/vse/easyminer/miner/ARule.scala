/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

case class ARule(antecedent: List[FixedValue],
                 consequent: List[FixedValue],
                 interestMeasures: InterestMeasures,
                 contingencyTable: ContingencyTable)

trait ARuleVisualizer {
  def aruleToString(arule: ARule): String
}