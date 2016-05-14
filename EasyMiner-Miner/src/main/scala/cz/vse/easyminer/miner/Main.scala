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
 * Created by propan on 25. 7. 2015.
 */
object Main extends App {

  actorSystem.scheduler.schedule(0 seconds, 30 seconds) {
    RConnectionPoolImpl.default.refresh()
  }(actorSystem.dispatcher)

  val service = actorSystem.actorOf(Props[MinerMainService], "main-service")

  IO(Http) ! Http.Bind(service, Conf().get[String]("easyminer.miner.rest.address"), port = Conf().get[Int]("easyminer.miner.rest.port"))

}
