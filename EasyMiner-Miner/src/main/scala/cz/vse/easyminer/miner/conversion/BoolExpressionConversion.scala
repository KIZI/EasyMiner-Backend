/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.conversion

import cz.vse.easyminer.miner.{BoolExpression, FixedValue, Value}

import scala.language.implicitConversions

/**
  * Created by Vaclav Zeman on 9. 10. 2016.
  */
trait BoolExpressionConversion {

  implicit def fixedValueToBoolExpression(fixedValue: FixedValue): BoolExpression[FixedValue] = Value(fixedValue)

  implicit def fixedValuesToBoolExpression(fixedValues: Seq[FixedValue]): BoolExpression[FixedValue] = fixedValues.iterator.map(fixedValueToBoolExpression).reduceLeft(_ AND _)

  implicit def fixedValuesToOptBoolExpression(fixedValues: Seq[FixedValue]): Option[BoolExpression[FixedValue]] = fixedValues.iterator.map(fixedValueToBoolExpression).reduceLeftOption(_ AND _)

}

object BoolExpressionConversion extends BoolExpressionConversion