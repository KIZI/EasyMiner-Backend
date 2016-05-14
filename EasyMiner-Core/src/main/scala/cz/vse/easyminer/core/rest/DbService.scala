package cz.vse.easyminer.core.rest

import akka.actor.{PoisonPill, Actor}
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.core.{HiveUserDatabase, MysqlUserDatabase}

/**
 * Created by propan on 9. 3. 2016.
 *
 * All database connections within dbConnectors are strongly bound with an Actor.
 * Without database connections the actor can not exists.
 * Therefore if any connector is not able to create a persistent connection, the actor will be stopped immediately
 */
trait DbService extends Actor {

  ue: UserEndpoint =>

  lazy val dbConnectors = {
    val process = getLimitedDb.zip(getUnlimitedDb).map {
      case (limitedDb, unlimitedDb) => buildDbConnectors(limitedDb, unlimitedDb)
    }
    process.onFailure {
      case _ => self ! PoisonPill
    }
    process
  }

  def buildDbConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: HiveUserDatabase): DBConnectors

}
