/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.core.util.BasicValidators.Exceptions

/**
 * Created by Vaclav Zeman on 22. 12. 2015.
 */
trait BasicValidators {

  case class MaxLength(maxLength: Int) extends Validator[String] {
    def defaultException(obj: String): ValidationException = new Exceptions.MaxLength(obj, maxLength)

    def validate(obj: String): Boolean = obj.length <= maxLength
  }

  object NonEmpty extends Validator[String] {
    def defaultException(obj: String): ValidationException = Exceptions.IsEmpty

    def validate(obj: String): Boolean = obj.nonEmpty
  }

  case class Greater[T](bound: T)(implicit num: Numeric[T]) extends Validator[T] {
    def defaultException(obj: T): ValidationException = new Exceptions.LowerThanOrEqual(obj.toString, bound.toString)

    def validate(obj: T): Boolean = num.gt(obj, bound)
  }

  case class GreaterOrEqual[T](bound: T)(implicit num: Numeric[T]) extends Validator[T] {
    def defaultException(obj: T): ValidationException = new Exceptions.LowerThan(obj.toString, bound.toString)

    def validate(obj: T): Boolean = num.gteq(obj, bound)
  }

  case class Lower[T](bound: T)(implicit num: Numeric[T]) extends Validator[T] {
    def defaultException(obj: T): ValidationException = new Exceptions.GreaterThanOrEqual(obj.toString, bound.toString)

    def validate(obj: T): Boolean = num.lt(obj, bound)
  }

  case class LowerOrEqual[T](bound: T)(implicit num: Numeric[T]) extends Validator[T] {
    def defaultException(obj: T): ValidationException = new Exceptions.GreaterThan(obj.toString, bound.toString)

    def validate(obj: T): Boolean = num.lteq(obj, bound)
  }

}

object BasicValidators extends BasicValidators {

  object Exceptions {

    class MaxLength(value: String, maxLength: Int) extends ValidationException(s"The value '$value' length is too large. Maximum number of characters is $maxLength.")

    class LowerThan(value: String, bound: String) extends ValidationException(s"The value '$value' is lower than $bound.")

    class GreaterThan(value: String, bound: String) extends ValidationException(s"The value '$value' is greater than $bound.")

    class LowerThanOrEqual(value: String, bound: String) extends ValidationException(s"The value '$value' is lower than or equal $bound.")

    class GreaterThanOrEqual(value: String, bound: String) extends ValidationException(s"The value '$value' is greater than or equal $bound.")

    object IsEmpty extends ValidationException("Value must not be empty.")

  }

}

trait CollectionValidators {

  object NonEmpty extends Validator[Traversable[_]] {
    def defaultException(obj: Traversable[_]): ValidationException = CollectionValidators.Exceptions.IsEmpty

    def validate(obj: Traversable[_]): Boolean = obj.nonEmpty
  }

}

object CollectionValidators extends CollectionValidators {

  object Exceptions {

    object IsEmpty extends ValidationException("Collection is empty.")

  }

}