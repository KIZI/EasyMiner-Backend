package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.miner.{Attribute, ARule, ARuleVisualizer, BoolExpressionVisualizer}

trait ARuleText extends ARuleVisualizer {

  self: BoolExpressionVisualizer[Attribute] =>

  def aruleToString(arule: ARule): String = arule.antecedent.map(exprToString).getOrElse("*") + " → " + exprToString(arule.consequent)

}
