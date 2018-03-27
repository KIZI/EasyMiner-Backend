package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.data.{ExclusiveIntervalBorder, InclusiveIntervalBorder, IntervalBorder}
import scalikejdbc._

/**
  * Created by propan on 11. 4. 2017.
  */
object IntervalBorderOps {

  implicit class PimpedSqlIntervalBorder(intervalBorder: IntervalBorder) {

    private def getSymbol(greaterThan: Boolean) = intervalBorder match {
      case _: InclusiveIntervalBorder if greaterThan => sqls">="
      case _: InclusiveIntervalBorder if !greaterThan => sqls"<="
      case _: ExclusiveIntervalBorder if greaterThan => sqls">"
      case _: ExclusiveIntervalBorder if !greaterThan => sqls"<"
    }

    /**
      * Check if the value is in interval [_, x] or [_, x)
      * Input value is a
      * if x >= a and x is inclusive then return true ELSE false
      * if x > a and x is exclusive then return true ELSE false
      *
      * @param x sql value
      * @return sql syntax comparision
      */
    def |>=|(x: SQLSyntax) = sqls"${intervalBorder.value} ${getSymbol(true)} $x"

    /**
      * Check if the value is in interval [x, _] or (x, _]
      * Input value is a
      * if x <= a and x is inclusive then return true ELSE false
      * if x < a and x is exclusive then return true ELSE false
      *
      * @param x sql value
      * @return sql syntax comparision
      */
    def |<=|(x: SQLSyntax) = sqls"${intervalBorder.value} ${getSymbol(false)} $x"

    /**
      * Check if the value is in interval [_, x] or [_, x)
      * Input value is a
      * if x >= a and x is inclusive then return true ELSE false
      * if x > a and x is exclusive then return true ELSE false
      *
      * @param x input value
      * @param n value numeric
      * @tparam T type of value
      * @return is in interval
      */
    def >=[T](x: T)(implicit n: Numeric[T]) = intervalBorder match {
      case InclusiveIntervalBorder(value) => value >= n.toDouble(x)
      case ExclusiveIntervalBorder(value) => value > n.toDouble(x)
    }

    /**
      * Check if the value is in interval [x, _] or (x, _]
      * Input value is a
      * if x <= a and x is inclusive then return true ELSE false
      * if x < a and x is exclusive then return true ELSE false
      *
      * @param x input value
      * @param n value numeric
      * @tparam T type of value
      * @return is in interval
      */
    def <=[T](x: T)(implicit n: Numeric[T]) = intervalBorder match {
      case InclusiveIntervalBorder(value) => value <= n.toDouble(x)
      case ExclusiveIntervalBorder(value) => value < n.toDouble(x)
    }

  }

}