/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import akka.util.Timeout

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * Created by Vaclav Zeman on 17. 8. 2015.
  */

/**
  * Extensions for Future object
  */
trait Concurrency {

  implicit class FutureOps[T](future: Future[T]) {
    /**
      * Get result of the future with timeout
      *
      * @param timeout implicit! timeout
      * @return result of the future object
      */
    def quickResultWithTimeout(implicit timeout: Timeout) = quickResult(timeout.duration)

    /**
      * Get result of the future with duration of waiting
      *
      * @param duration implicit! waiting time for a result (default is infinity)
      * @return result of the future object
      */
    def quickResult(implicit duration: Duration = Duration.Inf) = Await.result(future, duration)
  }

}

object Concurrency extends Concurrency