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

  implicit class PimpedInterval(interval: Interval) {
    def toIntervalString = {
      val round = BasicFunction.roundAt(6) _
      val leftCutPoint = interval.from match {
        case InclusiveIntervalBorder(value) => "[" + round(value)
        case ExclusiveIntervalBorder(value) => "(" + round(value)
      }
      val rightCutPoint = interval.to match {
        case InclusiveIntervalBorder(value) => round(value) + "]"
        case ExclusiveIntervalBorder(value) => round(value) + ")"
      }
      leftCutPoint + "," + rightCutPoint
    }
  }

}
