/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import java.security.MessageDigest

import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds
import scala.util.{Failure, Try}

object Match {
  def default: PartialFunction[Any, Unit] = {
    case _ =>
  }

  def apply[T](x: T)(body: PartialFunction[T, Unit]) = (body orElse default) (x)
}

object Lift {
  def default[U]: PartialFunction[Any, Option[U]] = {
    case _ => None
  }

  def apply[T, U](x: T)(body: PartialFunction[T, Option[U]]) = (body orElse default) (x)
}

object AutoLift {
  def apply[T, U](x: T)(body: PartialFunction[T, U]) = body.lift(x)
}

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

object BasicFunction {

  import scala.concurrent.duration._
  import scala.language.{postfixOps, reflectiveCalls}

  def tryClose[A, B <: {def close() : Unit}](closeable: B)(f: B => A): A = try {
    f(closeable)
  } finally {
    closeable.close()
  }

  def tryCloseBool[A, B <: {def close() : Boolean}](closeable: B)(f: B => A): A = try {
    f(closeable)
  } finally {
    closeable.close()
  }

  def optToThat[T]: PartialFunction[Option[T], T] = {
    case Some(x) => x
  }

  def mergeMaps[A, C <: Traversable[_]](m1: Map[A, C], m2: Map[A, C]) = m2.foldLeft(m1) {
    case (resultMap, (key, valueCol)) => resultMap + (key -> resultMap.get(key).map(x => (x ++ valueCol).asInstanceOf[C]).getOrElse(valueCol))
  }

  def md5(string: String) = new String(MessageDigest.getInstance("MD5").digest(string.getBytes))

  def limitedRepeatUntil[T](times: Int, waitingTime: Duration = 100 milliseconds)(until: T => Boolean)(f: => T) = {
    var repeated = 0
    repeatUntil(waitingTime)(until.andThen(_ || repeated >= times)) {
      repeated = repeated + 1
      f
    }
  }

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

  def roundAt(p: Int)(n: Double): Double = {
    val s = math pow(10, p);
    (math round n * s) / s
  }

}