/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer
package miner

import akka.actor.Props
import akka.io.IO
import cz.vse.easyminer.core.util.Conf
import cz.vse.easyminer.miner.impl.RConnectionPoolImpl
import cz.vse.easyminer.miner.rest.MinerMainService
import spray.can.Http

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by Vaclav Zeman on 25. 7. 2015.
  */

/**
  * Main object which starts HTTP service for this easyminer miner module
  */
object Main extends App {

  actorSystem.scheduler.schedule(0 seconds, 1 minute) {
    RConnectionPoolImpl.defaultMiner.refresh()
    RConnectionPoolImpl.defaultOutliers.refresh()
  }(actorSystem.dispatcher)

  val service = actorSystem.actorOf(Props[MinerMainService], "main-service")

  IO(Http) ! Http.Bind(service, Conf().get[String]("easyminer.miner.rest.address"), port = Conf().get[Int]("easyminer.miner.rest.port"))

}
