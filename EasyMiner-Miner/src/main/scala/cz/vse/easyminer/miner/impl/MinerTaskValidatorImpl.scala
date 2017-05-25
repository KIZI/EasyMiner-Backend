/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.miner._
import cz.vse.easyminer.miner.impl.MinerTaskValidatorImpl.Exceptions.BadInterestMeasureInput
import cz.vse.easyminer.preprocessing.UnlimitedDatasetType

trait MinerTaskValidatorImpl extends MinerTaskValidator {

  private def validateBoolExp[A](exp: BoolExpression[Attribute])(implicit f: Attribute => A, join: (A, A) => A): A = exp match {
    case ANDOR(a, b) => join(validateBoolExp[A](a), validateBoolExp[A](b))
    case NOT(a) => validateBoolExp[A](a)
    case Value(x) => f(x)
  }

  def validate(mt: MinerTask) = {
    if (!mt.interestMeasures.hasType[Limit]) throw new BadInterestMeasureInput("Rules limit is required.")
    if (!mt.interestMeasures.has(Auto)) {
      if (!mt.interestMeasures.hasType[Confidence]) throw new BadInterestMeasureInput("Confidence is required.")
      if (!mt.interestMeasures.hasType[Support]) throw new BadInterestMeasureInput("Support is required.")
      if (!mt.interestMeasures.hasType[MaxRuleLength]) throw new BadInterestMeasureInput("Max rule length is required.")
      if (!mt.interestMeasures.hasType[MinRuleLength]) throw new BadInterestMeasureInput("Min rule length is required.")
    }
    mt.interestMeasures.getAll.foreach {
      case Confidence(v) if v > 1 || v < 0.001 => throw new BadInterestMeasureInput("Confidence must be greater than 0.001 and less than 1.")
      case Support(v) if v > 1 || v < 0.001 => throw new BadInterestMeasureInput("Support must be greater than 0.001 and less than 1.")
      case Limit(v) if v <= 0 => throw new BadInterestMeasureInput("Limit must be greater than 0.")
      case MaxRuleLength(v) if v <= 0 => throw new BadInterestMeasureInput("Max rule length must be greater than 0.")
      case MinRuleLength(v) if v <= 0 => throw new BadInterestMeasureInput("Min rule length must be greater than 0.")
      case _ =>
    }
    if (mt.interestMeasures.hasType[MaxRuleLength] && mt.interestMeasures.hasType[MinRuleLength] && mt.interestMeasures.minlen > mt.interestMeasures.maxlen) {
      throw new BadInterestMeasureInput("Max rule length must equal to or be greater than min rule length.")
    }
    if (mt.interestMeasures.has(CBA) || mt.interestMeasures.has(Auto)) {
      val exception = if (mt.interestMeasures.has(Auto)) {
        new BadInterestMeasureInput("You may use only one attribute as the consequent if the CBA pruning is turned on.")
      } else {
        new BadInterestMeasureInput("You may use only one attribute as the consequent if the AUTO_CONF_SUPP parameter is turned on.")
      }
      val attributes = mt.consequent.map(x => validateBoolExp[Set[Int]](x)(
        {
          case AllValues(attribute) => Set(attribute.id)
          case FixedValue(attribute, _) => Set(attribute.id)
          case _ => throw exception
        },
        _ ++ _
      )).getOrElse(Set())
      if (attributes.size != 1) throw exception
    }
    if (mt.consequent.isEmpty && mt.datasetDetail.`type` == UnlimitedDatasetType) {
      throw new BadInterestMeasureInput("Mining without consequent is not allowed for unlimited datasets.")
    }
  }

}

object MinerTaskValidatorImpl {

  object Exceptions {

    class BadInterestMeasureInput(msg: String) extends ValidationException(msg)

  }

}