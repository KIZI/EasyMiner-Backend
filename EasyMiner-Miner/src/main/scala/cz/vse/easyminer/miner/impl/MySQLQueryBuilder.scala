package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.miner.{AND, AllValues, Attribute, BoolExpression, DatasetQueryBuilder, FixedValue, NOT, OR, Value}
import cz.vse.easyminer.preprocessing.AttributeDetail
import scalikejdbc._

trait MysqlQueryBuilder extends DatasetQueryBuilder {

  private class SqlSelectBuilder(implicit attributeToColName: AttributeDetail => SQLSyntax) {

    val ToSQLSelect: PartialFunction[(AttributeDetail, SQLSyntax), SQLSyntax] = {
      case (k, SQLSyntax("", _)) => k
      case (k, v) => nameAndValueToIf(k, v)
    }

    val ToSQLSelectMap: PartialFunction[(AttributeDetail, SQLSyntax), (AttributeDetail, SQLSyntax)] = {
      case (k, SQLSyntax("", _)) => k -> k
      case (k, v) => k -> nameAndValueToIf(k, v)
    }

    private def nameAndValueToIf(colName: SQLSyntax, condition: SQLSyntax) = sqls"IF($condition, $colName, NULL) AS $colName"

    private def joinSQLMaps(m1: Map[AttributeDetail, SQLSyntax], m2: Map[AttributeDetail, SQLSyntax], f: (SQLSyntax, SQLSyntax) => SQLSyntax) = m1.foldLeft(m2) {
      case (r, t @ (k, _)) if !r.contains(k) => r + t
      case (r, (k, v)) =>
        val rv = r(k)
        r + (k -> (if (rv.value.isEmpty || v.value.isEmpty) SQLSyntax.empty else f(rv, v)))
    }

    def toSQLMap(exp: BoolExpression[Attribute]): Map[AttributeDetail, SQLSyntax] = exp match {
      case AND(a, b) => joinSQLMaps(toSQLMap(a), toSQLMap(b), (a, b) => sqls"($a AND $b)")
      case OR(a, b) => joinSQLMaps(toSQLMap(a), toSQLMap(b), (a, b) => sqls"($a OR $b)")
      case Value(AllValues(a)) => Map(a -> SQLSyntax.empty)
      case Value(FixedValue(a, v)) => Map(a -> SQLSyntax.eq(a, v.value))
      case NOT(a) => toSQLMap(a) map { case (k, v) => k -> (if (v.value.isEmpty) v else sqls"NOT($v)") }
      case _ => Map.empty
    }

  }

  def toSQLSelect(exp: BoolExpression[Attribute])(implicit attributeToColName: AttributeDetail => SQLSyntax) = {
    val sqlSelectBuilder = new SqlSelectBuilder
    sqlSelectBuilder.toSQLMap(exp).collect(sqlSelectBuilder.ToSQLSelect)
  }

  def toSQLSelectMap(exp: BoolExpression[Attribute])(implicit attributeToColName: AttributeDetail => SQLSyntax) = {
    val sqlSelectBuilder = new SqlSelectBuilder
    sqlSelectBuilder.toSQLMap(exp).collect(sqlSelectBuilder.ToSQLSelectMap)
  }

}
