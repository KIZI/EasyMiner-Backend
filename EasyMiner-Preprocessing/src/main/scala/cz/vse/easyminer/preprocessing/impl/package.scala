/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.core.util.BasicFunction
import cz.vse.easyminer.data.{ExclusiveIntervalBorder, InclusiveIntervalBorder}
import cz.vse.easyminer.preprocessing.NumericIntervalsAttribute.Interval

/**
  * Created by Vaclav Zeman on 29. 11. 2016.
  */
package object impl {

  /**
    * Implicit operations for Interval class.
    * It adds function for conversion interval to string
    *
    * @param interval interval object
    */
  implicit class PimpedInterval(interval: Interval) {

    private def doubleToString(value: Double) = if (value == Double.NegativeInfinity) {
      "-Inf"
    } else if (value == Double.PositiveInfinity) {
      "Inf"
    } else {
      BasicFunction.roundAt(6)(value).toString
    }

    def toIntervalString = {
      val leftCutPoint = interval.from match {
        case InclusiveIntervalBorder(value) => "[" + doubleToString(value)
        case ExclusiveIntervalBorder(value) => "(" + doubleToString(value)
      }
      val rightCutPoint = interval.to match {
        case InclusiveIntervalBorder(value) => doubleToString(value) + "]"
        case ExclusiveIntervalBorder(value) => doubleToString(value) + ")"
      }
      leftCutPoint + "," + rightCutPoint
    }

  }

}
