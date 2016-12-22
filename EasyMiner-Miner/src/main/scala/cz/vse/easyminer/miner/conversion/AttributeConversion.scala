package cz.vse.easyminer.miner.conversion

import cz.vse.easyminer.miner.{BoolExpression, FixedValue}

/**
  * Created by propan on 9. 10. 2016.
  */
trait AttributeConversion {

}

object AttributeConversion extends AttributeConversion {

  implicit class PimpedFixedValueSeq(fixedValues: Seq[FixedValue]) extends BoolExpressionConversion {
    def toOptBoolExpression: Option[BoolExpression[FixedValue]] = fixedValues
  }

}