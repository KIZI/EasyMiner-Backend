package cz.vse.easyminer.core

/**
 * Created by propan on 20. 9. 2015.
 */
trait DependencyChecker[T] {

  val innerDependencyCheckers: Option[T => DependencyChecker.Runner]

  def check(): Unit

}

object DependencyChecker {

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
