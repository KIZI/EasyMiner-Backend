package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.data.NominalValue
import cz.vse.easyminer.miner._

import scala.language.implicitConversions

trait BoolExpressionText extends BoolExpressionVisualizer[Attribute] {

  val MappedFixedValue: MappedFixedValue

  def exprToString(expr: BoolExpression[Attribute]): String = expr match {
    case Value(AllValues(attribute)) => s"${attribute.name}(*)"
    case MappedFixedValue(attribute, NominalValue(value)) => s"${attribute.name}($value)"
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
    case MappedFixedValue(attribute, NominalValue(value)) => s"${attribute.name}($value)"
    case AND(a, b) => exprToString(a) + " & " + exprToString(b)
    case OR(a, b) => exprToString(a) + " | " + exprToString(b)
    case NOT(a) => "^" + exprToString(a)
    case _ => ""
  }

}

object BoolExpressionImpl {

  implicit class PimpedBoolExpression(expr: BoolExpression[Attribute]) {

    def toAttributes = {
      def nextToAttributes(expr: BoolExpression[Attribute]): Set[Attribute] = {
        expr match {
          case expr1 ANDOR expr2 => nextToAttributes(expr1) ++ nextToAttributes(expr2)
          case NOT(expr) => nextToAttributes(expr)
          case Value(attribute) => Set(attribute)
          case _ => Set.empty
        }
      }
      nextToAttributes(expr)
    }

    def toAttributeDetails = toAttributes collect {
      case AllValues(attributeDetail) => attributeDetail
      case FixedValue(attributeDetail, _) => attributeDetail
    }

  }

}