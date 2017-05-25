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
trait Concurrency {

  implicit class FutureOps[T](future: Future[T]) {
    def quickResultWithTimeout(implicit timeout: Timeout) = quickResult(timeout.duration)

    def quickResult(implicit duration: Duration = Duration.Inf) = Await.result(future, duration)
  }

}

object Concurrency extends Concurrency