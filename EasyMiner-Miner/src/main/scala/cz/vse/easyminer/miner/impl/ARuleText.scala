package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.miner._
import cz.vse.easyminer.miner.conversion.AttributeConversion.PimpedFixedValueSeq
import cz.vse.easyminer.miner.conversion.BoolExpressionConversion.fixedValuesToBoolExpression

trait ARuleText extends ARuleVisualizer {

  self: BoolExpressionVisualizer[Attribute] =>

  def aruleToString(arule: ARule): String = arule.antecedent.toOptBoolExpression.map(exprToString).getOrElse("*") + " → " + exprToString(arule.consequent)

}
