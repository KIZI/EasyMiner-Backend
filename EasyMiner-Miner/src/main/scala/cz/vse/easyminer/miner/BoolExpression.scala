/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import cz.vse.easyminer.core.util.AutoLift

sealed trait BoolExpression[+T] {
  def AND[A >: T](expr: BoolExpression[A]): BoolExpression[A] = new AND(this, expr)

  def OR[A >: T](expr: BoolExpression[A]): BoolExpression[A] = new OR(this, expr)

  def NOT: BoolExpression[T] = new NOT(this)
}

case class Value[T](x: T) extends BoolExpression[T]

case class AND[T](a: BoolExpression[T], b: BoolExpression[T]) extends BoolExpression[T]

case class OR[T](a: BoolExpression[T], b: BoolExpression[T]) extends BoolExpression[T]

case class NOT[T](a: BoolExpression[T]) extends BoolExpression[T]

object ANDOR {
  def unapply[T](expr: BoolExpression[T]) = AutoLift(expr) {
    case AND(a, b) => (a, b)
    case OR(a, b) => (a, b)
  }
}

object ANDORNOT {
  def unapply[T](expr: BoolExpression[T]) = expr match {
    case Value(_) => false
    case _ => true
  }
}

trait BoolExpressionVisualizer[T] {
  def exprToString(expr: BoolExpression[T]): String
}