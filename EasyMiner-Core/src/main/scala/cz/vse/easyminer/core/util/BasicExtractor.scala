/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

/**
  * Exctractor which tries to convert any to integer
  */
object AnyToInt {
  def unapply(s: Any): Option[Int] = try {
    if (s == null)
      None
    else
      Some(s match {
        case x: Int => x
        case x: Short => x.toInt
        case x: Byte => x.toInt
        case x => x.toString.toInt
      })
  } catch {
    case _: java.lang.NumberFormatException => None
  }
}

/**
  * Exctractor which tries to convert any to double
  */
object AnyToDouble {
  def unapply(s: Any): Option[Double] = try {
    if (s == null)
      None
    else
      Some(s match {
        case x: Int => x.toDouble
        case x: Double => x
        case x: Float => x.toDouble
        case x: Long => x.toDouble
        case x: Short => x.toDouble
        case x: Byte => x.toDouble
        case x => x.toString.toDouble
      })
  } catch {
    case _: java.lang.NumberFormatException => None
  }
}

/**
  * Extractor for new line character (it also accepts unicode new lines)
  */
object NewLine {
  def unapply(s: Char): Boolean = s match {
    case '\n' => true
    case '\r' => true
    case '\u0085' => true
    case '\u2028' => true
    case '\u2029' => true
    case _ => false
  }
}