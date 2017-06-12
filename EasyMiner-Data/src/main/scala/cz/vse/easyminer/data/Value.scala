/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

import java.text.NumberFormat

/**
  * Created by Vaclav Zeman on 3. 8. 2015.
  */

/**
  * Abstract for any value without any persistent information
  */
sealed trait Value

/**
  * Nominal value
  *
  * @param value string value
  */
case class NominalValue(value: String) extends Value

/**
  * Numeric value
  *
  * @param original number in the string format (original form before parsing)
  * @param value    numeric value
  */
case class NumericValue(original: String, value: Double) extends Value

/**
  * Empty value
  */
object NullValue extends Value

object NumericValue {

  /**
    * Create numeric value from a string
    * It throws exception if the number is not parsable
    *
    * @param value        string value
    * @param numberFormat number format - it depends on localization
    * @return numeric value
    */
  def apply(value: String)(implicit numberFormat: NumberFormat): NumericValue = NumericValue(value, numberFormat.parse(value.replace(' ', '\u00a0')).doubleValue())

}

/**
  * Detail of a value which is saved in database
  */
sealed trait ValueDetail {
  val id: Int
  val field: Int
  val frequency: Int
}

/**
  * Nominal value detail
  *
  * @param id        value id
  * @param field     field id
  * @param value     string value
  * @param frequency number of items (instances) which has this field-value
  */
case class NominalValueDetail(id: Int, field: Int, value: String, frequency: Int) extends ValueDetail

/**
  * Numeric value detail
  *
  * @param id        value id
  * @param field     field id
  * @param original  original string value
  * @param value     parsed numeric value
  * @param frequency number of items (instances) which has this field-value
  */
case class NumericValueDetail(id: Int, field: Int, original: String, value: Double, frequency: Int) extends ValueDetail

/**
  * Empty value detail
  *
  * @param id        value id
  * @param field     field id
  * @param frequency number of items (instances) which has this field-value
  */
case class NullValueDetail(id: Int, field: Int, frequency: Int) extends ValueDetail

/**
  * Value may also be an interval
  * Interval has two borders. This is abstraction for interval border which contains one double value
  */
sealed trait IntervalBorder {
  val value: Double
}

/**
  * Value of this interval border is included
  *
  * @param value included value
  */
case class InclusiveIntervalBorder(value: Double) extends IntervalBorder

/**
  * Value of this interval border is excluded
  *
  * @param value excluded value
  */
case class ExclusiveIntervalBorder(value: Double) extends IntervalBorder

/**
  * Abstraction for value interval which can have some frequency
  */
sealed trait ValueInterval {
  val frequency: Int
}

/**
  * Null interval has only frequency, no values
  *
  * @param frequency number of items (instances) which covers this interval
  */
case class NullValueInterval(frequency: Int) extends ValueInterval

/**
  * Typical interval
  *
  * @param from      interval border with min value
  * @param to        interval border with max value
  * @param frequency number of items (instances) which covers this interval
  */
case class NumericValueInterval(from: IntervalBorder, to: IntervalBorder, frequency: Int) extends ValueInterval