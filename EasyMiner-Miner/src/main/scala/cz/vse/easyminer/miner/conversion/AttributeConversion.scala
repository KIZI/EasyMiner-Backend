/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.conversion

import cz.vse.easyminer.miner.{BoolExpression, FixedValue}

/**
  * Created by Vaclav Zeman on 9. 10. 2016.
  */
trait AttributeConversion {

}

object AttributeConversion extends AttributeConversion {

  implicit class PimpedFixedValueSeq(fixedValues: Seq[FixedValue]) extends BoolExpressionConversion {
    def toOptBoolExpression: Option[BoolExpression[FixedValue]] = fixedValues
  }

}