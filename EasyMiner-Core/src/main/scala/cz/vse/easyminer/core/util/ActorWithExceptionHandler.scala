/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import akka.actor.{FSM, Actor}

/**
 * Created by Vaclav Zeman on 6. 9. 2015.
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
