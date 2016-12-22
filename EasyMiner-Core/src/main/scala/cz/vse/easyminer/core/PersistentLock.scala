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
  * Created by propan on 16. 2. 2016.
  */
case class PersistentLock(name: String, refreshTime: Date)

object PersistentLock {

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.core.PersistentLock")
  /**
    * Thread local variable of the current lock.
    * This is useful for inner lock which has the same name as the parent lock;
    * this situation should not lock the inner context.
    */
  private val currentLock = new DynamicVariable[String]("")

  val refreshTime = Conf().get[Duration]("easyminer.persistent-lock.refresh-time")
  val maxIdleTime = Conf().get[Duration]("easyminer.persistent-lock.max-idle-time")

  private class LockFactory(implicit lockTable: SQLSyntaxSupport[PersistentLock], mysqlDBConnector: MysqlDBConnector, actorRefFactory: ActorRefFactory) {

    import mysqlDBConnector._

    private def fetchLock(name: String)(implicit session: DBSession) = sql"SELECT ${lockTable.column.*} FROM ${lockTable.table} WHERE ${lockTable.column.name} = $name".map { wrs =>
      PersistentLock(wrs.string(lockTable.column.name), wrs.date(lockTable.column.refreshTime))
    }.first().apply()

    def createSchemaIfNotExists() = DBConn autoCommit { implicit session =>
      sql"""CREATE TABLE IF NOT EXISTS ${lockTable.table} (
      ${lockTable.column.name} varchar(255) NOT NULL,
      ${lockTable.column.refreshTime} datetime NOT NULL,
      PRIMARY KEY (${lockTable.column.name})
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8""".execute().apply()
    }

    def refreshLock(name: String): Boolean = DBConn autoCommit { implicit session =>
      sql"UPDATE ${lockTable.table} SET ${lockTable.column.refreshTime} = NOW() WHERE ${lockTable.column.name} = $name".executeUpdate().apply() > 0
    }

    def releaseLock(name: String) = DBConn autoCommit { implicit session =>
      sql"DELETE FROM ${lockTable.table} WHERE ${lockTable.column.name} = $name".execute().apply()
    }

    def lock(name: String): Boolean = try {
      DBConn autoCommit { implicit session =>
        sql"DELETE FROM ${lockTable.table} WHERE DATE_ADD(${lockTable.column.refreshTime}, INTERVAL ${maxIdleTime.toMicros} MICROSECOND) < NOW()".execute().apply()
      }
      DBConn localTx { implicit session =>
        if (fetchLock(name).isEmpty) {
          sql"INSERT INTO ${lockTable.table} (${lockTable.column.name}, ${lockTable.column.refreshTime}) VALUES ($name, NOW())".execute().apply()
          true
        } else {
          false
        }
      }
    } catch {
      case _: SQLIntegrityConstraintViolationException => false
    }

  }

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