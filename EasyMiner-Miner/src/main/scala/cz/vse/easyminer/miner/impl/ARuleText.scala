/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.miner._
import cz.vse.easyminer.miner.conversion.AttributeConversion.PimpedFixedValueSeq
import cz.vse.easyminer.miner.conversion.BoolExpressionConversion.fixedValuesToBoolExpression

/**
  * Convert association rule to text form
  */
trait ARuleText extends ARuleVisualizer {

  self: BoolExpressionVisualizer[Attribute] =>

  def aruleToString(arule: ARule): String = arule.antecedent.toOptBoolExpression.map(exprToString).getOrElse("*") + " → " + exprToString(arule.consequent)

}
