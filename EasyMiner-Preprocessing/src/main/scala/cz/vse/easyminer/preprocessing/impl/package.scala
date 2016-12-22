package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.core.util.BasicFunction
import cz.vse.easyminer.data.{ExclusiveIntervalBorder, InclusiveIntervalBorder}
import cz.vse.easyminer.preprocessing.NumericIntervalsAttribute.Interval

/**
  * Created by propan on 29. 11. 2016.
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
