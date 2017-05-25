/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer
package preprocessing

import akka.actor._
import akka.io.IO
import cz.vse.easyminer.core.util.Conf
import cz.vse.easyminer.preprocessing.rest.PreprocessingMainService
import spray.can.Http

import scala.language.postfixOps

/**
 * Created by Vaclav Zeman on 15. 7. 2015.
 */
object Main extends App {

  val service = actorSystem.actorOf(Props[PreprocessingMainService], "main-service")

  IO(Http) ! Http.Bind(service, Conf().get[String]("easyminer.preprocessing.rest.address"), port = Conf().get[Int]("easyminer.preprocessing.rest.port"))

}
