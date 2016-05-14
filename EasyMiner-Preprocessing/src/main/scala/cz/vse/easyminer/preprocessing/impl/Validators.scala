package cz.vse.easyminer.preprocessing.impl

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.Validator.{UnitException, ValidationException}
import cz.vse.easyminer.core.util.{Conf, BasicValidators}
import cz.vse.easyminer.preprocessing.{Attribute, Dataset}

/**
 * Created by propan on 29. 1. 2016.
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

    implicit object AttributeValidator extends Validator[Attribute] {
      def defaultException(obj: Attribute): ValidationException = UnitException

      def validate(obj: Attribute): Boolean = {
        Validator(obj.name)(MaxLength(tableColMaxlen))
        Validator(obj.name)(NonEmpty)
        true
      }
    }

  }

  val tableNameMaxlen = Conf().get[Int]("easyminer.preprocessing.table-name-maxlen")

  val tableColMaxlen = Conf().get[Int]("easyminer.preprocessing.table-col-maxlen")


}
