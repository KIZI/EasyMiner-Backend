/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core

import cz.vse.easyminer.core.Validator.ValidationException

/**
  * Created by Vaclav Zeman on 20. 8. 2015.
  */

/**
  * Trait for validation of some object
  * This validates object, if it is not valid then throws exception
  *
  * @tparam T type of validated object
  */
trait Validator[-T] {

  /**
    * Default exception if the validate function returns false
    *
    * @param obj validated object
    * @return exception
    */
  def defaultException(obj: T): ValidationException

  /**
    * Validate object
    *
    * @param obj validated object
    * @return true = is valid, false is not valid
    */
  def validate(obj: T): Boolean

}

/**
  * Object for validation some instance
  */
object Validator {

  /**
    * Default exception with some message. It is also bad request exception for web service validation
    *
    * @param msg error message
    */
  class ValidationException(msg: String) extends Exception("Invalid input data: " + msg) with StatusCodeException.BadRequest

  /**
    * Unit exception with default message
    */
  object UnitException extends ValidationException("Unexpected exception.")

  /**
    * This validates some object.
    * It needs implicit validator for the type of the object
    * You can add a message. if the object is invalid, then ValidationException with this message will be thrown.
    * If the message is empty, then default exception of the Validator will be thrown
    *
    * @param obj       validated object
    * @param msg       error message (optional)
    * @param validator implicit! Validator for the object type
    * @tparam T type of the validated object
    */
  def apply[T](obj: T, msg: String = "")(implicit validator: Validator[T]) = if (!validator.validate(obj)) {
    throw if (msg.isEmpty) validator.defaultException(obj) else new ValidationException(msg)
  }

}