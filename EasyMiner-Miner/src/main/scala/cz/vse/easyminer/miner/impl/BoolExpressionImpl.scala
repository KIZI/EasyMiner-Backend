/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.data.NominalValue
import cz.vse.easyminer.miner._

import scala.language.implicitConversions

/**
  * Convert rule statement to text form
  */
trait BoolExpressionText extends BoolExpressionVisualizer[Attribute] {

  val MappedFixedValue: MappedFixedValue

  /**
    * Convert rule statement to text form
    *
    * @param expr statement
    * @return text
    */
  def exprToString(expr: BoolExpression[Attribute]): String = expr match {
    case Value(AllValues(attribute)) => s"${attribute.name}(*)"
    case MappedFixedValue(attribute, NominalValue(value)) => s"${attribute.name}($value)"
    case AND(a, b) => "( " + exprToString(a) + " & " + exprToString(b) + " )"
    case OR(a, b) => "( " + exprToString(a) + " | " + exprToString(b) + " )"
    case NOT(a) => "^( " + exprToString(a) + " )"
    case _ => ""
  }

}

/**
  * Convert rule statement to text form
  */
trait BoolExpressionShortText extends BoolExpressionVisualizer[Attribute] {

  val MappedFixedValue: MappedFixedValue

  /**
    * Convert rule statement to text form
    *
    * @param expr statement
    * @return text
    */
  def exprToString(expr: BoolExpression[Attribute]): String = expr match {
    case Value(AllValues(attribute)) => s"${attribute.name}(*)"
    case MappedFixedValue(attribute, NominalValue(value)) => s"${attribute.name}($value)"
    case AND(a, b) => exprToString(a) + " & " + exprToString(b)
    case OR(a, b) => exprToString(a) + " | " + exprToString(b)
    case NOT(a) => "^" + exprToString(a)
    case _ => ""
  }

}

/**
  * Additional operations for rule statements
  */
object BoolExpressionImpl {

  /**
    * Extension of rule statement
    *
    * @param expr rule statement
    */
  implicit class PimpedBoolExpression(expr: BoolExpression[Attribute]) {

    /**
      * Get all attributes from the statement
      *
      * @return attributes
      */
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

    /**
      * Get all attribute detail from the statement
      *
      * @return attribute details
      */
    def toAttributeDetails = toAttributes collect {
      case AllValues(attributeDetail) => attributeDetail
      case FixedValue(attributeDetail, _) => attributeDetail
    }

  }

}