/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.util.BasicValidators.Greater
import cz.vse.easyminer.data._
import scalikejdbc._

import scala.annotation.tailrec

/**
  * Created by Vaclav Zeman on 28. 1. 2016.
  */

/**
  * This is main implementation for making aggregated histogram from list of numeric values within some field.
  * Aggregated histogram consists of intervals
  * The final implementation of this trait must contain only information about sql symbols and table, stats and method for sql execution which returns bins
  * Returned intervals are equidistant
  */
trait DbValueHistogramOps extends ValueHistogramOps {

  case class Bin(middlePoint: Option[Double], frequency: Int)

  sealed trait ColumnStats

  case class NumericColumnStats(minValue: Double, maxValue: Double) extends ColumnStats

  object NoneColumnStats extends ColumnStats

  class ValueTableProperties(val tableName: TableDefSQLSyntax,
                             val valueNumericColName: SQLSyntax,
                             val frequencyColName: SQLSyntax,
                             val valueColumnColName: SQLSyntax)

  /**
    * Information about table name and columns names
    */
  protected[this] val valueTableProperties: ValueTableProperties
  /**
    * Modulo symbol for a specific sql database
    */
  protected[this] val sqlModuloSymbol: SQLSyntax
  /**
    * Column/field id
    */
  protected[this] val columnId: Int
  /**
    * Field stats; we need to know only min and max values
    */
  protected[this] val columnStats: ColumnStats

  /**
    * Input is sql query which should return sequence of bins (intervals).
    * This method only takes this query and execute it within some specific connector
    *
    * @param sql sql query
    * @return sequence of intervals/bins
    */
  protected[this] def histogramSqlQuery(sql: SQLToList[Bin, HasExtractor]): Seq[Bin]

  private def getNumericIntervalsData(maxBins: Int, min: IntervalBorder, max: IntervalBorder): Seq[Bin] = {
    val range = max.value - min.value
    val intervalSize = range / maxBins
    val minWhere = min match {
      case InclusiveIntervalBorder(value) => sqls"${valueTableProperties.valueNumericColName} >= $value"
      case ExclusiveIntervalBorder(value) => sqls"${valueTableProperties.valueNumericColName} > $value"
    }
    val maxWhere = max match {
      case InclusiveIntervalBorder(value) => sqls"${valueTableProperties.valueNumericColName} <= $value"
      case ExclusiveIntervalBorder(value) => sqls"${valueTableProperties.valueNumericColName} < $value"
    }
    val group = sqls"(${valueTableProperties.valueNumericColName} - ((${valueTableProperties.valueNumericColName} - ${min.value}) $sqlModuloSymbol $intervalSize))"
    histogramSqlQuery(
      sql"""
      SELECT $group + ($intervalSize / 2) AS `middle`, SUM(${valueTableProperties.frequencyColName}) AS `frequency`
      FROM ${valueTableProperties.tableName}
      WHERE ${valueTableProperties.valueColumnColName} = $columnId AND ($minWhere AND $maxWhere OR ${valueTableProperties.valueNumericColName} IS NULL)
      GROUP BY $group
      """.map(rs => Bin(rs.doubleOpt("middle"), rs.int("frequency"))).list()
    )
  }

  /**
    * For some data source and field this function return aggregated values/instances histogram by number of bins.
    *
    * @param maxBins  maximal number of bins
    * @param minValue minimal value of the histogram
    * @param maxValue maximal value of the histogram
    * @return intervals which represents a histogram
    */
  final def getHistogram(maxBins: Int, minValue: Option[IntervalBorder] = None, maxValue: Option[IntervalBorder] = None): Seq[ValueInterval] = columnStats match {
    case NumericColumnStats(minColumnValue, maxColumnValue) =>
      val min = minValue.getOrElse(InclusiveIntervalBorder(minColumnValue))
      val max = maxValue.getOrElse(InclusiveIntervalBorder(maxColumnValue))
      Validator(max.value)(Greater(min.value))
      val range = max.value - min.value
      val intervalSize = range / maxBins
      val data = getNumericIntervalsData(maxBins, min, max)
      @tailrec
      def fillBins(bins: List[Double], data: Seq[(Double, Int)], result: Vector[ValueInterval] = Vector()): Vector[ValueInterval] = {
        val interval = bins.take(2)
        if (interval.size == 2) {
          val binFrom = if (interval.head == min.value) min else InclusiveIntervalBorder(interval.head)
          val binTo = if (interval.tail.head == max.value) max else ExclusiveIntervalBorder(interval.tail.head)
          def sumRestDataIfEnd(data: Seq[(Double, Int)]) = if (bins.size == 2) data.foldLeft(0)(_ + _._2) else 0
          data match {
            case Seq((x, freq), tail@_*) if x >= binFrom.value && x < binTo.value =>
              fillBins(bins.tail, tail, result :+ NumericValueInterval(binFrom, binTo, freq + sumRestDataIfEnd(tail)))
            case _ =>
              fillBins(bins.tail, data, result :+ NumericValueInterval(binFrom, binTo, 0 + sumRestDataIfEnd(data)))
          }
        } else {
          result
        }
      }
      fillBins((min.value to max.value by intervalSize).toList, data.view.collect { case Bin(Some(x), y) => x -> y })
    /*data.collectFirst {
      case Bin(None, freq) => NullValueInterval(freq) +: result
    }.getOrElse(result)*/
    case NoneColumnStats => Nil
  }

}
