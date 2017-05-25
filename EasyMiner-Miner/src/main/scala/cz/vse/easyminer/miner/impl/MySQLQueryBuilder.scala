/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.miner._
import scalikejdbc._

trait MysqlQueryBuilder extends DatasetQueryBuilder {

  val attributeColumn: SQLSyntax

  val itemColumn: SQLSyntax

  private def buildSqlConditionFromExpression(exp: BoolExpression[Attribute]): SQLSyntax = exp match {
    case AND(a, b) => buildSqlConditionFromExpression(a) or buildSqlConditionFromExpression(b)
    case OR(a, b) => buildSqlConditionFromExpression(a) or buildSqlConditionFromExpression(b)
    case Value(AllValues(a)) => sqls"$attributeColumn = ${a.id}"
    case Value(FixedValue(a, v)) => sqls"$attributeColumn = ${a.id} AND $itemColumn = $v"
    case NOT(a) => sqls"NOT(${buildSqlConditionFromExpression(a)})"
    case _ => sqls"0"
  }

  def toSqlConditions(exp: BoolExpression[Attribute]): scalikejdbc.SQLSyntax = buildSqlConditionFromExpression(exp)

}
