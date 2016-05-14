package cz.vse.easyminer.data.impl

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.Validator.{UnitException, ValidationException}
import cz.vse.easyminer.core.util.{BasicValidators, Conf}
import cz.vse.easyminer.data._

/**
 * Created by propan on 20. 8. 2015.
 */
object Validators {

  import BasicValidators._

  trait FieldValidators {

    implicit object FieldValidator extends Validator[Field] {
      def defaultException(obj: Field): ValidationException = UnitException

      def validate(obj: Field): Boolean = {
        Validator(obj.name)(MaxLength(tableColMaxlen))
        Validator(obj.name)(NonEmpty)
        true
      }
    }

  }

  trait ValueValidators {

    implicit object ValueValidator extends Validator[Value] {
      def defaultException(obj: Value): ValidationException = UnitException

      def validate(obj: Value): Boolean = obj match {
        case NominalValue(value) =>
          Validator(value)(MaxLength(tableColMaxlen))
          true
        case _ => true
      }
    }

  }

  trait DataSourceValidators {

    implicit object DataSourceValidator extends Validator[DataSource] {
      def defaultException(obj: DataSource): ValidationException = UnitException

      def validate(obj: DataSource): Boolean = {
        Validator(obj.name)(MaxLength(tableNameMaxlen))
        Validator(obj.name)(NonEmpty)
        true
      }
    }

  }

  val tableNameMaxlen = Conf().get[Int]("easyminer.data.table-name-maxlen")

  val tableColMaxlen = Conf().get[Int]("easyminer.data.table-col-maxlen")

}
