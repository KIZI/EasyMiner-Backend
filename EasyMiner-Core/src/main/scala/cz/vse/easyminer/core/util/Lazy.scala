/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

/**
  * Created by Vaclav Zeman on 16. 8. 2015.
  */

import scala.language.implicitConversions

/**
  * Methods for using of the Lazy class
  */
object Lazy {

  /**
    * This wraps some lazy value f with Lazy object
    *
    * @param f lazy value
    * @tparam A result of the lazy value
    * @return Lazy object (value f has not been yet evaluated)
    */
  def lazily[A](f: => A): Lazy[A] = new Lazy(f)

  /**
    * This implicit function converts lazy value "x" as Lazy(x) into "x".
    * So then it works like "lazy val" syntax
    *
    * @param l Lazy object with lazy x value
    * @tparam A result of the lazy value
    * @return result of the lazy value
    */
  implicit def evalLazy[A](l: Lazy[A]): A = l()

}

/**
  * Lazy wrapper for some value/function f.
  * This works like lazy val, but this "lazy val" has also isEvaluated method, which detects whether the lazy val has been evaluated.
  *
  * @param f lazy value
  * @tparam A result type of lazy value
  */
class Lazy[A] private(f: => A) {

  private var option: Option[A] = None

  /**
    * Evaluate lazy value
    *
    * @return result of the lazy value
    */
  def apply(): A = this.synchronized {
    option match {
      case Some(a) => a
      case None => val a = f; option = Some(a); a
    }
  }

  /**
    * Return true if lazy value has been evaluated
    *
    * @return true/false
    */
  def isEvaluated: Boolean = this.synchronized {
    option.isDefined
  }

}