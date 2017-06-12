/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.util.BasicValidators.{GreaterOrEqual, LowerOrEqual}
import cz.vse.easyminer.preprocessing._

/**
  * Created by Vaclav Zeman on 29. 1. 2016.
  */

/**
  * This is a decorator for value operations object which validates all input parameters and data
  *
  * @param valueOps value operations object
  */
class ValidationValueOps(valueOps: ValueOps) extends ValueOps {

  val dataset: DatasetDetail = valueOps.dataset

  val attribute: AttributeDetail = valueOps.attribute

  def getValues(offset: Int, limit: Int): Seq[ValueDetail] = {
    Validator(offset)(GreaterOrEqual(0))
    Validator(limit)(GreaterOrEqual(1))
    Validator(limit)(LowerOrEqual(1000))
    valueOps.getValues(offset, limit)
  }

}