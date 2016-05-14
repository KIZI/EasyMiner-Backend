package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.data.{NominalValue, NullValue, NumericValue}
import cz.vse.easyminer.miner._

import scala.language.implicitConversions

trait BoolExpressionText extends BoolExpressionVisualizer[Attribute] {

  val MappedFixedValue: MappedFixedValue

  def exprToString(expr: BoolExpression[Attribute]): String = expr match {
    case Value(AllValues(attribute)) => s"${attribute.name}(*)"
    case MappedFixedValue(attribute, value) => value match {
      case NominalValue(value) => s"${attribute.name}($value)"
      case NumericValue(value) => s"${attribute.name}($value)"
      case NullValue => s"${attribute.name}(null)"
    }
    case AND(a, b) => "( " + exprToString(a) + " & " + exprToString(b) + " )"
    case OR(a, b) => "( " + exprToString(a) + " | " + exprToString(b) + " )"
    case NOT(a) => "^( " + exprToString(a) + " )"
    case _ => ""
  }

}

trait BoolExpressionShortText extends BoolExpressionVisualizer[Attribute] {

  val MappedFixedValue: MappedFixedValue

  def exprToString(expr: BoolExpression[Attribute]): String = expr match {
    case Value(AllValues(attribute)) => s"${attribute.name}(*)"
    case MappedFixedValue(attribute, value) => value match {
      case NominalValue(value) => s"${attribute.name}($value)"
      case NumericValue(value) => s"${attribute.name}($value)"
      case NullValue => s"${attribute.name}(null)"
    }
    case AND(a, b) => exprToString(a) + " & " + exprToString(b)
    case OR(a, b) => exprToString(a) + " | " + exprToString(b)
    case NOT(a) => "^" + exprToString(a)
    case _ => ""
  }

}