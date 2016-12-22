package cz.vse.easyminer.data

import java.text.NumberFormat

/**
  * Created by propan on 3. 8. 2015.
  */
sealed trait Value

case class NominalValue(value: String) extends Value

case class NumericValue(original: String, value: Double) extends Value

object NullValue extends Value

object NumericValue {
  def apply(value: String)(implicit numberFormat: NumberFormat): NumericValue = NumericValue(value, numberFormat.parse(value.replace(' ', '\u00a0')).doubleValue())
}

sealed trait ValueDetail {
  val id: Int
  val field: Int
  val frequency: Int
}

case class NominalValueDetail(id: Int, field: Int, value: String, frequency: Int) extends ValueDetail

case class NumericValueDetail(id: Int, field: Int, original: String, value: Double, frequency: Int) extends ValueDetail

case class NullValueDetail(id: Int, field: Int, frequency: Int) extends ValueDetail

sealed trait IntervalBorder {
  val value: Double
}

case class InclusiveIntervalBorder(value: Double) extends IntervalBorder

case class ExclusiveIntervalBorder(value: Double) extends IntervalBorder

sealed trait ValueInterval {
  val frequency: Int
}

case class NullValueInterval(frequency: Int) extends ValueInterval

case class NumericValueInterval(from: IntervalBorder, to: IntervalBorder, frequency: Int) extends ValueInterval