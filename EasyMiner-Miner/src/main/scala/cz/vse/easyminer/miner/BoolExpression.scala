/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import cz.vse.easyminer.core.util.AutoLift

/**
  * This expression represents association rule statement on left or right side.
  * Statement is consisted of atoms which are connected by logic coupling (And, Or, Not)
  *
  * @tparam T
  */
sealed trait BoolExpression[+T] {
  def AND[A >: T](expr: BoolExpression[A]): BoolExpression[A] = new AND(this, expr)

  def OR[A >: T](expr: BoolExpression[A]): BoolExpression[A] = new OR(this, expr)

  def NOT: BoolExpression[T] = new NOT(this)
}

/**
  * Atom of a statement
  *
  * @param x atom value
  * @tparam T type of atom
  */
case class Value[T](x: T) extends BoolExpression[T]

/**
  * Boolean AND connector for statement
  *
  * @param a statement a
  * @param b statement b
  * @tparam T type of atom
  */
case class AND[T](a: BoolExpression[T], b: BoolExpression[T]) extends BoolExpression[T]

/**
  * Boolean OR connector for statement
  *
  * @param a statement a
  * @param b statement b
  * @tparam T type of atom
  */
case class OR[T](a: BoolExpression[T], b: BoolExpression[T]) extends BoolExpression[T]

/**
  * Negation of a statement
  *
  * @param a statement
  * @tparam T type of atom
  */
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

/**
  * Trait for stringify a statement
  *
  * @tparam T type of atom
  */
trait BoolExpressionVisualizer[T] {
  def exprToString(expr: BoolExpression[T]): String
}