package cz.vse.easyminer.core.dependencies

import akka.actor.ActorRefFactory
import akka.util.Timeout
import cz.vse.easyminer.core.DependencyChecker
import cz.vse.easyminer.core.DependencyChecker.{DependecyCheckerException, Runner}
import cz.vse.easyminer.core.util.{Concurrency, Conf}
import spray.client.pipelining._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by propan on 20. 9. 2015.
 */
class UserServiceDependencyChecker(implicit actorRefFactory: ActorRefFactory) extends DependencyChecker[Nothing] {

  import Concurrency._

  val innerDependencyCheckers: Option[(Nothing) => Runner] = None

  private implicit val ec = actorRefFactory.dispatcher

  private lazy val pipeline = sendReceive

  def check(): Unit = {
    val url = Conf().get[String]("easyminer.user.http-endpoint") + Conf().get[String]("easyminer.dependency-checker.user.checking-path")
    if (pipeline(Get(url)).quickResultWithTimeout(Timeout(10 seconds)).status.intValue != 200) {
      throw new DependecyCheckerException(this)
    }
  }
}
