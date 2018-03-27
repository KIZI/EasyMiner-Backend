package cz.vse.easyminer.core.db

import cz.vse.easyminer.core.hadoop.Yarn
import cz.vse.easyminer.core.util.{AnyToDouble, BasicFunction, Match}
import org.apache.hive.jdbc.HiveStatement
import scalikejdbc.GeneralizedTypeConstraintsForWithExtractor.=:=
import scalikejdbc.{ConnectionPoolContext, DBSession, HasExtractor, NoConnectionPoolContext, SQL, SQLExecution, SQLToResult, SQLUpdate, SQLUpdateWithGeneratedKey, WithExtractor}

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.{higherKinds, postfixOps}
import scala.util.{Success, Try}

/**
  * Created by propan on 25. 5. 2016.
  */
class HiveQueryController private(hiveStatement: HiveStatement)(implicit ec: ExecutionContext) {

  private var currentState = 0.0

  private object StateLock

  val jobId = {
    val promisedJobId = Promise[String]()
    Future {
      val Loader = "map\\s+=\\s+([.\\d]+)%,\\s+reduce\\s+=\\s+([.\\d]+)%".r.unanchored
      val JobId = "(job_\\d+_\\d+)".r.unanchored
      BasicFunction.repeatUntil[Boolean](1 second)(_ == true) {
        if (hiveStatement.hasMoreLogs) {
          for {
            logs <- Try(hiveStatement.getQueryLog)
            log <- logs
          } Match(log) {
            case Loader(AnyToDouble(mapState), AnyToDouble(reduceState)) => StateLock.synchronized {
              currentState = (mapState + reduceState) / 2
            }
            case JobId(jobId) if !promisedJobId.isCompleted =>
              promisedJobId.success(jobId)
          }
          false
        } else {
          true
        }
      }
    }.onComplete { _ =>
      StateLock.synchronized(currentState = 1.0)
      if (!promisedJobId.isCompleted) promisedJobId.failure(new NoSuchElementException("Any map/reduce job was not created within this query."))
    }
    promisedJobId.future
  }

  def loader: Double = StateLock.synchronized(currentState)

  def status = jobId.value.collect {
    case Success(strJobId) => Yarn.applicationId(strJobId).map(Yarn.applicationState)
  }.flatten

  def isCompleted = loader == 1

  def kill() = for {
    strJobId <- jobId
    applicationId <- Yarn.applicationId(strJobId)
  } {
    Yarn.kill(applicationId)
  }

}

object HiveQueryController {

  trait HiveQueryControllerExecutor[T] {

    protected[this] def execute: T

    def applyWithControl(f: HiveQueryController => Unit)(implicit ec: ExecutionContext) = HiveConnectionProxy.withQueryState { futureHiveStatement =>
      futureHiveStatement.foreach(hiveStatement => f(new HiveQueryController(hiveStatement)))
      execute
    }

  }

  implicit class PimpedSQLExecution(sqlExecution: SQLExecution)(implicit session: DBSession) extends HiveQueryControllerExecutor[Boolean] {
    protected[this] def execute: Boolean = sqlExecution.apply()
  }

  implicit class PimpedSQLUpdate(sqlExecution: SQLUpdate)(implicit session: DBSession) extends HiveQueryControllerExecutor[Int] {
    protected[this] def execute: Int = sqlExecution.apply()
  }

  implicit class PimpedSQLUpdateWithGeneratedKey(sqlExecution: SQLUpdateWithGeneratedKey)(implicit session: DBSession) extends HiveQueryControllerExecutor[Long] {
    protected[this] def execute: Long = sqlExecution.apply()
  }

  implicit class PimpedSQLToResult[A, E <: WithExtractor, C[_]](sqlExecution: SQLToResult[A, E, C])
                                                               (implicit session: DBSession,
                                                                context: ConnectionPoolContext = NoConnectionPoolContext,
                                                                hasExtractor: SQL[A, E] =:= SQL[A, HasExtractor]) extends HiveQueryControllerExecutor[C[A]] {
    protected[this] def execute: C[A] = sqlExecution.apply()
  }

}