package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.util.BasicValidators.{GreaterOrEqual, LowerOrEqual}
import cz.vse.easyminer.preprocessing._

/**
 * Created by propan on 29. 1. 2016.
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