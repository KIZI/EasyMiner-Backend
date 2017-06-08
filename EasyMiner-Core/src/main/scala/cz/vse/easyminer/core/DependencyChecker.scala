/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core

/**
  * Created by Vaclav Zeman on 20. 9. 2015.
  *
  * This trait checks whether some parts of easyminer system are working and cooperates with each other
  * @tparam T result of a dependecy checker
  */
trait DependencyChecker[T] {

  val innerDependencyCheckers: Option[T => DependencyChecker.Runner]

  def check(): Unit

}

/**
  * This object applies dependency checkers
  */
object DependencyChecker {

  /**
    * This function creates dependency checker runner from checkers
    * @param dependencyCheckers dependency checkers
    * @return dependecy checker runner
    */
  def apply(dependencyCheckers: DependencyChecker[_]*) = Runner(dependencyCheckers: _*)

  private object Runner {
    def apply(dependencyCheckers: DependencyChecker[_]*) = new Runner(dependencyCheckers: _*)
  }

  class Runner private(dependencyCheckers: DependencyChecker[_]*) {
    def check() = for (dependencyChecker <- dependencyCheckers) {
      try {
        dependencyChecker.check()
      } catch {
        case ex: DependecyCheckerException => throw ex
        case ex: Throwable => throw new DependecyCheckerException(dependencyChecker, ex)
      }
    }
  }

  class DependecyCheckerException(dch: DependencyChecker[_], cause: Throwable) extends Exception(s"Dependency ${dch.getClass.getSimpleName} is not available.", cause) with StatusCodeException.ServiceUnavailable {
    def this(dch: DependencyChecker[_]) = this(dch, null)
  }

}
