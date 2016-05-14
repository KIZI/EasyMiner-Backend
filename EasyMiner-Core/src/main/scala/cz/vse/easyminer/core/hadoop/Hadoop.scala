package cz.vse.easyminer
package core.hadoop

import java.io.File

import cz.vse.easyminer.core.util.Conf
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.security.UserGroupInformation

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by propan on 9. 3. 2016.
  */
object Hadoop {

  val conf = new Configuration()

  Conf().get[String]("easyminer.hadoop.config-paths").split(",").iterator.map(new File(_).toURI.toURL).foreach(conf.addResource)

  UserGroupInformation.setConfiguration(conf)

  val authType = if (Conf().get[String]("easyminer.hadoop.auth.type") == "kerberos") {
    UserGroupInformation.setLoginUser(
      UserGroupInformation.getBestUGI(
        Conf().get[String]("easyminer.hadoop.auth.kerberos-ticket-cache-path"),
        Conf().get[String]("easyminer.hadoop.auth.kerberos-username")
      )
    )
    AuthType.Kerberos
  } else {
    AuthType.Simple
  }

  if (authType == AuthType.Kerberos) {
    implicit val ec = actorSystem.dispatcher
    actorSystem.scheduler.schedule(1 hour, 1 hour) {
      UserGroupInformation.getLoginUser.reloginFromTicketCache()
    }
  }

  sealed trait AuthType

  object AuthType {

    object Simple extends AuthType

    object Kerberos extends AuthType

  }

}
