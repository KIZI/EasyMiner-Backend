/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.Validator.{UnitException, ValidationException}
import cz.vse.easyminer.core.util.{BasicValidators, CollectionValidators, Conf, Match}
import cz.vse.easyminer.data.InclusiveIntervalBorder
import cz.vse.easyminer.preprocessing.NumericIntervalsAttribute.Interval
import cz.vse.easyminer.preprocessing._

/**
  * Created by Vaclav Zeman on 29. 1. 2016.
  */
object Validators {

  import BasicValidators._

  trait DatasetValidators {

    implicit object DatasetValidator extends Validator[Dataset] {
      def defaultException(obj: Dataset): ValidationException = UnitException

      def validate(obj: Dataset): Boolean = {
        Validator(obj.name)(MaxLength(tableNameMaxlen))
        Validator(obj.name)(NonEmpty)
        true
      }
    }

  }

  trait AttributeValidators {

    implicit object IntervalValidator extends Validator[NumericIntervalsAttribute.Interval] {
      def defaultException(obj: Interval): ValidationException = new ValidationException("Invalid interval " + obj.toIntervalString)

      def validate(obj: Interval): Boolean = obj.from.value < obj.to.value || obj.from.value == obj.to.value && obj.from.isInstanceOf[InclusiveIntervalBorder] && obj.to.isInstanceOf[InclusiveIntervalBorder]
    }

    implicit object AttributeValidator extends Validator[Attribute] {
      def defaultException(obj: Attribute): ValidationException = UnitException

      def validate(obj: Attribute): Boolean = {
        Validator(obj.name)(MaxLength(tableColMaxlen))
        Validator(obj.name)(NonEmpty)
        Match(obj) {
          case NominalEnumerationAttribute(_, _, bins) =>
            Validator(bins)(CollectionValidators.NonEmpty)
            Validator(bins.length)(LowerOrEqual(maxBins))
            bins.foreach { bin =>
              Validator(bin.name)(MaxLength(tableColMaxlen))
              Validator(bin.name)(NonEmpty)
              Validator(bin.values)(CollectionValidators.NonEmpty)
            }
          case NumericIntervalsAttribute(_, _, bins) =>
            Validator(bins)(CollectionValidators.NonEmpty)
            Validator(bins.length)(LowerOrEqual(maxBins))
            bins.foreach { bin =>
              Validator(bin.name)(MaxLength(tableColMaxlen))
              Validator(bin.name)(NonEmpty)
              Validator(bin.intervals)(CollectionValidators.NonEmpty)
              bin.intervals.foreach(interval => Validator(interval))
            }
          case EquidistantIntervalsAttribute(_, _, bins) =>
            Validator(bins)(Greater(0))
            Validator(bins)(LowerOrEqual(maxBins))
          case EquifrequentIntervalsAttribute(_, _, bins) =>
            Validator(bins)(Greater(0))
            Validator(bins)(LowerOrEqual(maxBins))
          case EquisizedIntervalsAttribute(_, _, support) =>
            Validator(support)(Greater(1.0 / maxBins))
            Validator(support)(LowerOrEqual(1.0))
        }
        true
      }
    }

    implicit object AttributeSeqValidator extends Validator[Seq[Attribute]] {
      def defaultException(obj: Seq[Attribute]): ValidationException = UnitException

      def validate(obj: Seq[Attribute]): Boolean = {
        obj.foreach(attribute => Validator(attribute))
        true
      }
    }

  }

  val tableNameMaxlen = Conf().get[Int]("easyminer.preprocessing.table-name-maxlen")

  val tableColMaxlen = Conf().get[Int]("easyminer.preprocessing.table-col-maxlen")

  val maxBins = 1000

}
