package cz.vse.easyminer.core.util

import akka.actor.{FSM, Actor}

/**
 * Created by propan on 6. 9. 2015.
 */
trait ActorWithExceptionHandler extends Actor {

  def handleException(ex: Exception): Unit

  def exceptionHandling(f: Receive) = new Receive {
    def isDefinedAt(x: Any): Boolean = f.isDefinedAt(x)

    def apply(v1: Any): Unit = try {
      f(v1)
    } catch {
      case ex: Exception => handleException(ex)
    }
  }

}


trait FSMWithExceptionHandler[S, D] extends FSM[S, D] {

  def handleException(ex: Exception): State

  def exceptionHandling(f: StateFunction) = new StateFunction {
    def isDefinedAt(x: Event): Boolean = f.isDefinedAt(x)

    def apply(v1: Event): State = try {
      f(v1)
    } catch {
      case ex: Exception => handleException(ex)
    }
  }

}
