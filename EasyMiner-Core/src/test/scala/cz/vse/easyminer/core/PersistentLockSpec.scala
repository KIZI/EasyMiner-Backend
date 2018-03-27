package cz.vse.easyminer
package core

import cz.vse.easyminer.core.db.MysqlDBConnector
import org.scalatest.{FlatSpec, Matchers}
import scalikejdbc._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
 * Created by propan on 16. 2. 2016.
 */
class PersistentLockSpec extends FlatSpec with Matchers with ConfOpt {

  implicit val mysqlDBConnector: MysqlDBConnector = new MysqlDBConnector(mysqlUserDatabase)
  implicit val ex: ExecutionContext = actorSystem.dispatcher

  implicit object LockTable extends SQLSyntaxSupport[PersistentLock] {
    override def columns: Seq[String] = List("name", "refresh_time")
  }

  "PersistentLocker" should "provide an exclusive lock for one type of a context" in {
    val successfulFutures = List(
      Future {
        PersistentLock("test1") {
          Thread.sleep(2000)
        }
      },
      Future {
        PersistentLock("test2") {
          Thread.sleep(2000)
        }
      },
      Future {
        PersistentLock("test3") {
          Thread.sleep(2000)
        }
      }
    )
    successfulFutures.foreach(Await.result(_, 15 seconds))
  }

  it should "deny access of different contexts within one lock" in {
    val successFuture = Future {
      PersistentLock("test1") {
        Thread.sleep(2000)
      }
    }
    Thread.sleep(500)
    val failureFuture = Future {
      PersistentLock("test1") {
        Thread.sleep(2000)
      }
    }
    Await.result(successFuture, 15 seconds)
    intercept[PersistentLock.Exceptions.LockedContext] {
      Await.result(failureFuture, 15 seconds)
    }
  }

  it should "allow a serial access of different contexts within one lock" in {
    val future1 = Future {
      PersistentLock("test1") {
        Thread.sleep(1000)
      }
    }
    Await.result(future1, 15 seconds)
    val future2 = Future {
      PersistentLock("test1") {
        Thread.sleep(1000)
      }
    }
    Await.result(future2, 15 seconds)
  }

  it should "allow inner locks with the same name as the parent lock" in {
    PersistentLock("test1") {
      PersistentLock("test1") {
        Thread.sleep(500)
      }
    }
    PersistentLock("test1") {
      PersistentLock("test1") {
        PersistentLock("test2") {
          intercept[PersistentLock.Exceptions.LockedContext] {
            PersistentLock("test1") {
              Thread.sleep(500)
            }
          }
        }
      }
    }
  }

}
