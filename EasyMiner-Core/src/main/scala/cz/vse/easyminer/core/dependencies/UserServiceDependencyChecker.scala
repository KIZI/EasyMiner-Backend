/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

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
  * Created by Vaclav Zeman on 20. 9. 2015.
  */

/**
  * It checks, whether required user service is accessible
  *
  * @param actorRefFactory actor system for calling user service page by URL
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
