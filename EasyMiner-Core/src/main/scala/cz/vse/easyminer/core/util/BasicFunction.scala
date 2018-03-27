/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import java.security.MessageDigest
import java.util.UUID

import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds
import scala.util.{Failure, Random, Try}

/**
  * This can be used for pattern-matching which need not be completed. Each case must return Unit
  * Example: Match(x) {
  * case x: Int => println("is integer")
  * } // if x is not integer, then no message is printed
  */
object Match {
  def default: PartialFunction[Any, Unit] = {
    case _ =>
  }

  def apply[T](x: T)(body: PartialFunction[T, Unit]) = (body orElse default) (x)
}

/**
  * It is same as Match object, but it returns Option.
  * Every case must return Option
  * Some - if there is some case which returns Some
  * None - no case is matched or some case returns None
  */
object Lift {
  def default[U]: PartialFunction[Any, Option[U]] = {
    case _ => None
  }

  def apply[T, U](x: T)(body: PartialFunction[T, Option[U]]) = (body orElse default) (x)
}

/**
  * It is same as Lift object, but each case need not return Option - it supports any type
  * Some - if there is some case
  * None - no case is matched
  */
object AutoLift {
  def apply[T, U](x: T)(body: PartialFunction[T, U]) = body.lift(x)
}

/**
  * Extensions for Map class
  */
object MapOps {

  implicit class PimpedMap[A, B](map: Map[A, B]) {

    def applyAndUpdate(key: A)(update: B => B) = map.get(key) match {
      case Some(value) => map.updated(key, update(value))
      case None => map
    }

    /**
      * Get an item from the map by a key. If the key does not exist, then the default is used as the item.
      * After getting item the item is updated
      */
    def applyOrElseAndUpdate(key: A, default: => B)(update: B => B) = map.updated(key, update(map.getOrElse(key, default)))

    /**
      * Get an item from the map by a key and then update it. If the key does not exist, then the default is added to the map without updating.
      */
    def applyAndUpdateOrElse(key: A, default: => B)(update: B => B) = map.updated(key, map.get(key).map(update).getOrElse(default))

  }

}

/**
  * Commons functions for easyminer scripts
  */
object BasicFunction {

  import scala.concurrent.duration._
  import scala.language.{postfixOps, reflectiveCalls}

  /**
    * Try-close block. If you call f function within this method and input is closable, then after completion of function f the close() method is automatically fired.
    *
    * @param closeable closable input object
    * @param f         function, after which the close() method will be fired
    * @tparam A output type for function f
    * @tparam B input closable type for function f
    * @return output type B
    */
  def tryClose[A, B <: {def close() : Unit}](closeable: B)(f: B => A): A = try {
    f(closeable)
  } finally {
    closeable.close()
  }

  /**
    * It is same as tryClose, but close() method returns Boolean (not Unit)
    *
    * @param closeable closable input object
    * @param f         function, after which the close() method will be fired
    * @tparam A output type for function f
    * @tparam B input closable type for function f
    * @return output type B
    */
  def tryCloseBool[A, B <: {def close() : Boolean}](closeable: B)(f: B => A): A = try {
    f(closeable)
  } finally {
    closeable.close()
  }

  /**
    * Partial function which converts Some(x) into x
    *
    * @tparam T output type
    * @return type T
    */
  def optToThat[T]: PartialFunction[Option[T], T] = {
    case Some(x) => x
  }

  /**
    * This function merges two maps, where value is some collection.
    * m2 collection is appended into m1 collection with the same key.
    * m2 collection is added into m1 Map if m1 does not contain the m2 key
    * Example: m1: Map(1 -> List(1), 2 -> Nil), m2: Map(1 -> List(2), 3 -> Nil)
    * Result is: Map(1 -> List(1, 2), 2 -> Nil, 3 -> Nil)
    *
    * @param m1 map1
    * @param m2 map2
    * @tparam A key type
    * @tparam C collection type
    * @return merged maps within one larger map
    */
  def mergeMaps[A, C <: Traversable[_]](m1: Map[A, C], m2: Map[A, C]) = m2.foldLeft(m1) {
    case (resultMap, (key, valueCol)) => resultMap + (key -> resultMap.get(key).map(x => (x ++ valueCol).asInstanceOf[C]).getOrElse(valueCol))
  }

  /**
    * Hashing md5 function
    *
    * @param string any string
    * @return hashed string
    */
  def md5(string: String) = new String(MessageDigest.getInstance("MD5").digest(string.getBytes))

  /**
    * It is same as repeatUntil but the number of repeats is limited
    *
    * @param times       max number of repeats
    * @param waitingTime if "until" function is false then wait the waitingTime and repeat function f
    * @param until       output of f function is delegated to "until" function, which returns boolean (true = it is ok, return output of the f function; false = repeat function f)
    * @param f           repeatable function
    * @tparam T output of the f function
    * @return output of the f function
    */
  def limitedRepeatUntil[T](times: Int, waitingTime: Duration = 100 milliseconds)(until: T => Boolean)(f: => T) = {
    var repeated = 0
    repeatUntil(waitingTime)(until.andThen(_ || repeated >= times)) {
      repeated = repeated + 1
      f
    }
  }

  /**
    * This methods repeats function f while the condition "until" is false
    *
    * @param waitingTime if "until" function is false then wait the waitingTime and repeat function f
    * @param until       output of f function is delegated to "until" function, which returns boolean (true = it is ok, return output of the f function; false = repeat function f)
    * @param f           repeatable function
    * @tparam T output of the f function
    * @return output of the f function
    */
  @tailrec
  def repeatUntil[T](waitingTime: Duration = 100 milliseconds)(until: T => Boolean)(f: => T): T = {
    val rf = f
    if (until(rf)) {
      rf
    } else {
      Thread.sleep(waitingTime.toMillis)
      repeatUntil(waitingTime)(until)(f)
    }
  }

  /**
    * The main input of this function is collection of Try objects.
    * If any Try is failure then other successful Try objects are rollbacked by a "rollback" function and an exception of the failed Try object will be thrown.
    * If all Try are successful then it returns the same collection but not as Try objects with type T, but as T objects
    *
    * @param transactions collection of Try objects
    * @param rollback     rollback function for all successful Try objects. Input of this function is result type of Try object
    * @param bf           implicit! builder for collection of Try objects
    * @tparam A    type of results in Try objects
    * @tparam Repr type of collection
    * @tparam That type of result collection
    * @return result collection or exception if any Try is failed
    */
  def rollbackIfFailure[A, Repr[+X] <: Traversable[X], That](transactions: Repr[Try[A]])(rollback: A => Unit)(implicit bf: CanBuildFrom[Repr[_], A, That]): That = transactions.find(_.isFailure) match {
    case Some(Failure(th)) =>
      transactions.foreach(_.foreach(rollback))
      throw th
    case _ =>
      val b = bf(transactions)
      b.sizeHint(transactions)
      transactions.foreach(x => b += x.get)
      b.result()
  }

  /**
    * This rounds double with precision p
    *
    * @param p precision
    * @param n input number
    * @return output rounded number
    */
  def roundAt(p: Int)(n: Double): Double = {
    val s = math pow(10, p)
    (math round n * s) / s
  }

  def randomString(num: Int) = Random.alphanumeric.take(num).mkString

  def uuidhash(num: Int)(uUID: UUID) = {
    def intToChar(number: Int) = {
      val modNumber = number % 62
      if (modNumber < 10) {
        (modNumber + 48).toChar
      } else if (modNumber <= 35) {
        (modNumber + 55).toChar
      } else {
        (modNumber + 61).toChar
      }
    }
    val bitArray = MessageDigest.getInstance("MD5").digest(uUID.toString.getBytes)
    (0 until num).iterator.map(x => bitArray(x % 16) & 0xFF).scanLeft(0)(_ + _).drop(1).map(intToChar).mkString
  }

}