package cz.vse.easyminer.core.util

import akka.util.Timeout

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * Created by propan on 17. 8. 2015.
  */
trait Concurrency {

  implicit class FutureOps[T](future: Future[T]) {
    def quickResultWithTimeout(implicit timeout: Timeout) = quickResult(timeout.duration)

    def quickResult(implicit duration: Duration = Duration.Inf) = Await.result(future, duration)
  }

}

object Concurrency extends Concurrency