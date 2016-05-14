package cz.vse.easyminer.core

import cz.vse.easyminer.core.Validator.ValidationException

/**
 * Created by propan on 20. 8. 2015.
 */
trait Validator[-T] {

  def defaultException(obj: T): ValidationException

  def validate(obj: T): Boolean

}

object Validator {

  class ValidationException(msg: String) extends Exception("Invalid input data: " + msg) with StatusCodeException.BadRequest

  object UnitException extends ValidationException("Unexpected exception.")

  def apply[T](obj: T, msg: String = "")(implicit validator: Validator[T]) = if (!validator.validate(obj)) {
    throw if (msg.isEmpty) validator.defaultException(obj) else new ValidationException(msg)
  }

}