/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core

import java.sql.SQLIntegrityConstraintViolationException
import java.util.Date

import akka.actor._
import cz.vse.easyminer.core.PersistentLock.Exceptions.LockedContext
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.Conf
import org.slf4j.LoggerFactory
import scalikejdbc._

import scala.concurrent.duration.{Duration, _}
import scala.language.postfixOps
import scala.util.{DynamicVariable, Failure, Try}

/**
  * This library takes care about locking some parts of action, which can not run in parallel
  * Created by Vaclav Zeman on 16. 2. 2016.
  */

/**
  * Locking object
  *
  * @param name        name of a lock
  * @param refreshTime refresh time for a lock
  */
case class PersistentLock(name: String, refreshTime: Date)

/**
  * This object starts a block of a script which can not be run in parallel.
  * One lock with a specific name can be created simultaneously only once, then other lock with the same name must wait.
  * All locks are persistent, if some script falls and does not finish itself then the lock is still active within persisten database.
  * After some idle time, all inactive locks are released.
  */
object PersistentLock {

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.core.PersistentLock")
  /**
    * Thread local variable of the current lock.
    * This is useful for inner lock which has the same name as the parent lock;
    * this situation should not lock the inner context.
    */
  private val currentLock = new DynamicVariable[String]("")

  /**
    * If some script under a lock is in progress, then the timestamp of the lock is refreshing in some specific period.
    */
  val refreshTime = Conf().get[Duration]("easyminer.persistent-lock.refresh-time")

  /**
    * After the lapse of time when the process is idle, the lock is automaticly released
    */
  val maxIdleTime = Conf().get[Duration]("easyminer.persistent-lock.max-idle-time")

  /**
    * This is a lock factory class with a specific database, table and actor system
    *
    * @param lockTable        database table for locks
    * @param mysqlDBConnector database connection
    * @param actorRefFactory  actor system
    */
  private class LockFactory(implicit lockTable: SQLSyntaxSupport[PersistentLock], mysqlDBConnector: MysqlDBConnector, actorRefFactory: ActorRefFactory) {

    import mysqlDBConnector._

    /**
      * This creates database schema for locks if it does not exist
      *
      * @return true/false
      */
    def createSchemaIfNotExists() = DBConn autoCommit { implicit session =>
      sql"""CREATE TABLE IF NOT EXISTS ${lockTable.table} (
      ${lockTable.column.name} varchar(255) NOT NULL,
      ${lockTable.column.refreshTime} datetime NOT NULL,
      PRIMARY KEY (${lockTable.column.name})
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8""".execute().apply()
    }

    /**
      * Refresh the lock timestamp with the current time
      *
      * @param name lock name
      * @return true = lock was refreshed, false = lock does not exist
      */
    def refreshLock(name: String): Boolean = DBConn autoCommit { implicit session =>
      sql"UPDATE ${lockTable.table} SET ${lockTable.column.refreshTime} = NOW() WHERE ${lockTable.column.name} = $name".executeUpdate().apply() > 0
    }

    /**
      * Delete (release) a lock from the database
      *
      * @param name lock name
      * @return true/false
      */
    def releaseLock(name: String) = DBConn autoCommit { implicit session =>
      sql"DELETE FROM ${lockTable.table} WHERE ${lockTable.column.name} = $name".execute().apply()
    }

    /**
      * Delete all idle locks and create a new lock with a specific name
      *
      * @param name lock name
      * @return true = the lock was created, false = the lock exists
      */
    def lock(name: String): Boolean = try {
      DBConn autoCommit { implicit session =>
        sql"DELETE FROM ${lockTable.table} WHERE DATE_ADD(${lockTable.column.refreshTime}, INTERVAL ${maxIdleTime.toMicros} MICROSECOND) < NOW()".execute().apply()
        sql"INSERT INTO ${lockTable.table} (${lockTable.column.name}, ${lockTable.column.refreshTime}) VALUES ($name, NOW())".execute().apply()
      }
      true
    } catch {
      case _: SQLIntegrityConstraintViolationException => false
    }

  }

  /**
    * Actor for a lock
    * This is for a periodic refresh of the lock timestamp
    *
    * @param name        lock name
    * @param lockFactory lock factory
    */
  private class LockRefreshActor(name: String, lockFactory: LockFactory) extends Actor {

    context.setReceiveTimeout(refreshTime)

    def receive: Receive = {
      case ReceiveTimeout => if (!lockFactory.refreshLock(name)) context stop self
    }

    override def postStop(): Unit = logger.debug(s"Lock '$name': refresh actor has been stopped.")

  }

  private object LockRefreshActor {
    def props(name: String, lockFactory: LockFactory): Props = Props(new LockRefreshActor(name: String, lockFactory: LockFactory))
  }

  /**
    * This applies a function f under a specific lock.
    * If the lock exists and the wait flag is true, then this wait for finish of the previous process and then run it. If the wait flag is false then it throws an exception.
    * If this function is fired within another function which is under the lock and has the same lock name, then it will be run within existed lock as an inner function.
    * Schema: Time1 => apply f1 with lock A
    * Time2 => apply f2 with lock A - wait for finish of f1
    * Time3 => end of f1, release lock A and create new one fro f2
    * Time4 => apply f3 with lock A in f2 - no waiting, this is run immediately
    *
    * @param name             lock name
    * @param wait             true = wait for finishing of the previous function with the same lock, false = if the lock exists the throws exception
    * @param f                the main function which is under the lock
    * @param lockTable        implicit! table for locks
    * @param mysqlDBConnector implicit! database conection
    * @param actorRefFactory  implicit! actor system
    * @tparam T type which is returned by function f
    * @return type T
    */
  def apply[T](name: String, wait: Boolean = false)(f: => T)(implicit lockTable: SQLSyntaxSupport[PersistentLock], mysqlDBConnector: MysqlDBConnector, actorRefFactory: ActorRefFactory): T = {
    val lockFactory = new LockFactory
    lockFactory.createSchemaIfNotExists()
    def lockAndFire = Try {
      if (lockFactory.lock(name)) {
        logger.debug(s"Lock '$name': this lock is free. The context has locked it for its exclusive usage.")
        val lockRefreshActor = actorRefFactory.actorOf(LockRefreshActor.props(name, lockFactory), PersistentLock.getClass.getName + "-" + name)
        try {
          currentLock.withValue(name)(f)
        } finally {
          actorRefFactory stop lockRefreshActor
          lockFactory.releaseLock(name)
          logger.debug(s"Lock '$name': the context has been finished; therefore the lock has been released.")
        }
      } else if (currentLock.value == name) {
        logger.debug(s"Lock '$name': an inner context within this lock is being performed.")
        try {
          f
        } finally {
          logger.debug(s"Lock '$name': the inner context within this lock has been finished.")
        }
      } else {
        logger.debug(s"Lock '$name': another context uses this lock.")
        throw new LockedContext(name)
      }
    }
    def successfullyLocked(result: Try[T]) = result match {
      case Failure(_: LockedContext) => false
      case _ => true
    }
    if (wait) {
      limitedRepeatUntil[Try[T]](3600, 1 second)(successfullyLocked)(lockAndFire).get
    } else {
      lockAndFire.get
    }
  }

  object Exceptions {

    class LockedContext(name: String) extends Exception(s"The context '$name' is now locked. Wait a moment and try it again later.") with StatusCodeException.Locked

  }

}