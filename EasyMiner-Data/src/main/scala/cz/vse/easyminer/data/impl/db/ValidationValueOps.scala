/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.data._
import cz.vse.easyminer.core.util.BasicValidators.{GreaterOrEqual, LowerOrEqual}

/**
  * Created by Vaclav Zeman on 1. 9. 2015.
  */

/**
  * This is a decorator for value operations object which validates all input parameters
  *
  * @param ops value operations object
  */
class ValidationValueOps(ops: ValueOps) extends ValueOps {

  val dataSource: DataSourceDetail = ops.dataSource
  val field: FieldDetail = ops.field

  def getHistogram(maxBins: Int, minValue: Option[IntervalBorder] = None, maxValue: Option[IntervalBorder] = None): Seq[ValueInterval] = {
    Validator(maxBins)(GreaterOrEqual(2))
    Validator(maxBins)(LowerOrEqual(1000))
    ops.getHistogram(maxBins, minValue, maxValue)
  }

  def getNumericStats: Option[FieldNumericDetail] = ops.getNumericStats

  def getValues(offset: Int, limit: Int): Seq[ValueDetail] = {
    Validator(offset)(GreaterOrEqual(0))
    Validator(limit)(GreaterOrEqual(1))
    Validator(limit)(LowerOrEqual(1000))
    ops.getValues(offset, limit)
  }

}
