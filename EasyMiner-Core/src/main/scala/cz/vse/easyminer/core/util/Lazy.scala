package cz.vse.easyminer.core.util

/**
 * Created by propan on 16. 8. 2015.
 */
import scala.language.implicitConversions

object Lazy {

  def lazily[A](f: => A): Lazy[A] = new Lazy(f)

  implicit def evalLazy[A](l: Lazy[A]): A = l()

}

class Lazy[A] private(f: => A) {

  private var option: Option[A] = None

  def apply(): A = this.synchronized {
    option match {
      case Some(a) => a
      case None => val a = f; option = Some(a); a
    }
  }

  def isEvaluated: Boolean = this.synchronized {
    option.isDefined
  }

}