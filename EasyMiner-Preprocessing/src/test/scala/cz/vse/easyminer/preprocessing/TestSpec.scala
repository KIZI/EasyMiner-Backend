package cz.vse.easyminer.preprocessing

import akka.actor.{Actor, Props}
import cz.vse.easyminer._
import cz.vse.easyminer.core.PersistentLock
import cz.vse.easyminer.core.db.MysqlDBConnector
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import scalikejdbc._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by propan on 16. 2. 2016.
  */
class TestSpec extends FlatSpec with Matchers with TemplateOpt with BeforeAndAfterAll with MysqlDataDbOps {

  val mysqlDBConnector: MysqlDBConnector = DBSpec.makeMysqlConnector(true)
  implicit val ec = actorSystem.dispatcher

  implicit object LockTable extends SQLSyntaxSupport[PersistentLock] {
    override def columns: Seq[String] = List("name", "refresh_time")
  }

  class TaskQueue[T](tasks: collection.mutable.Queue[(() => T, Promise[T])], maxTasks: Int = 5) extends Actor {

    var hasFailureTask = false
    var occupiedSlots = 0

    override def preStart(): Unit = (0 until maxTasks).foreach(_ => self ! Request.NewTask())

    object Request {

      case class NewTask(freeSlot: Boolean = false)

      object Failed

    }

    def receive: Receive = {
      case Request.Failed => hasFailureTask = true
      case Request.NewTask(freeSlot) =>
        if (freeSlot) occupiedSlots = occupiedSlots - 1
        if (tasks.isEmpty && occupiedSlots == 0) {
          context stop self
        } else if (tasks.nonEmpty) {
          if (hasFailureTask) {
            tasks.dequeue()._2.failure(TaskQueue.OtherTaskFailed)
            self ! Request.NewTask()
          } else {
            val (f, promise) = tasks.dequeue()
            val future = Future {
              f()
            }
            future.onComplete {
              case Success(_) =>
                self ! Request.NewTask(true)
              case Failure(_) =>
                self ! Request.Failed
                self ! Request.NewTask(true)
            }
            promise.completeWith(future)
            occupiedSlots = occupiedSlots + 1
          }
        }
    }

  }

  object TaskQueue {

    object OtherTaskFailed extends Exception

    def apply[A, B](inputs: Seq[A])(task: A => B): Seq[Try[B]] = {
      val promises = inputs.map(_ => Promise[B]())
      actorSystem.actorOf(Props(new TaskQueue(collection.mutable.Queue[(() => B, Promise[B])](inputs.map(x => () => task(x)).zip(promises): _*))))
      promises.map(x => Await.ready(x.future, Duration.Inf).value).collect {
        case Some(x: Success[B]) => x
        case Some(x@Failure(th)) if th != OtherTaskFailed => x
      }
    }

  }

  "neco" should "neco" in {
    HiveSpec.apply((_, _) => {})
    DBSpec.apply(_ => {})
    /*val a = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
    val b = TaskQueue(a) { a =>
      println(s"start $a")
      Thread.sleep(1000 + Random.nextInt(4000))
      if (a == 4) throw new Exception("error")
      println(s"end $a")
      a
    }
    println(b)*/
  }

  //import mysqlDBConnector._

  //import ExecutionContext.Implicits._

  /*"neco" should "neco" ignore {
    DBConn autoCommit { implicit session =>
      sql"INSERT INTO ${DataSourceTable.table} (${DataSourceTable.column.name}, ${DataSourceTable.column.size}) VALUES ('test1', 0)".execute().apply()
      sql"INSERT INTO ${DataSourceTable.table} (${DataSourceTable.column.name}, ${DataSourceTable.column.size}) VALUES ('test2', 0)".execute().apply()
      sql"INSERT INTO ${DataSourceTable.table} (${DataSourceTable.column.name}, ${DataSourceTable.column.size}) VALUES ('test3', 0)".execute().apply()
    }
    def testTx(num: Int) = {
      Thread.sleep(Random.nextInt(2000))
      val a = DBConn
      //sql"SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE".execute().apply()(a.autoCommitSession())
      a localTx { implicit session =>
        println(session.connection.hashCode())
        val n = sql"SELECT ${DataSourceTable.column.size} FROM ${DataSourceTable.table} WHERE ${DataSourceTable.column.name} = 'test1' FOR UPDATE".map(_.int(1)).first().apply().get
        println(s"$num: lock = $n")
        Thread.sleep(Random.nextInt(2000))
        if (n == 0) {
          sql"UPDATE ${DataSourceTable.table} SET ${DataSourceTable.column.size} = 1 WHERE ${DataSourceTable.column.name} = 'test1'".execute().apply()
          println(s"$num: update lock to 1")
          Thread.sleep(Random.nextInt(2000))
          val b = sql"SELECT ${DataSourceTable.column.size} FROM ${DataSourceTable.table} WHERE ${DataSourceTable.column.name} = 'test2'".map(_.int(1)).first().apply().get
          println(s"$num: value is $b")
          Thread.sleep(Random.nextInt(2000))
          sql"UPDATE ${DataSourceTable.table} SET ${DataSourceTable.column.size} = ${b + 1} WHERE ${DataSourceTable.column.name} = 'test2'".execute().apply()
          println(s"$num: updated value to ${b + 1}")
          Thread.sleep(Random.nextInt(2000))
          sql"UPDATE ${DataSourceTable.table} SET ${DataSourceTable.column.size} = 0 WHERE ${DataSourceTable.column.name} = 'test1'".execute().apply()
          println(s"$num: update lock to 0")
        }
      }
    }
    val futures = for (i <- 0 until 10) yield {
      Future(testTx(i))
    }
    for (future <- futures) {
      Await.result(future, 60 seconds)
    }
  }

  it should "neco2" in {
    def testTx(num: Int) = {
      val tableName = "test" + (if (num < 4) 1 else num)
      Thread.sleep(Random.nextInt(2000))
      //val a = DBConn
      //sql"SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE".execute().apply()(a.autoCommitSession())
      try {
      DBConn autoCommit { implicit session =>
        sql"DELETE FROM ${LockTable.table} WHERE DATE_ADD(${LockTable.column.refreshTime}, INTERVAL 5000000 MICROSECOND) < NOW()".execute().apply()
        Thread.sleep(Random.nextInt(2000))
        sql"INSERT INTO ${LockTable.table} (${LockTable.column.name}, ${LockTable.column.refreshTime}) VALUES ($tableName, NOW())".execute().apply()
        println(s"$num: lock")
        Thread.sleep(Random.nextInt(2000))
        val n = sql"UPDATE ${LockTable.table} SET ${LockTable.column.refreshTime} = NOW() WHERE ${LockTable.column.name} = $tableName".executeUpdate().apply()
        println(s"$num: updated $n")
        Thread.sleep(Random.nextInt(2000))
      }
      } catch {
        case _: SQLIntegrityConstraintViolationException =>
      }
    }
    val futures = for (i <- 0 until 10) yield {
      Future(testTx(i))
    }
    for (future <- futures) {
      Await.result(future, 60 seconds)
    }
  }

  override protected def beforeAll(): Unit = {}

  override protected def afterAll(): Unit = {
    //DBSpec.rollbackData()
    mysqlDBConnector.close()
  }*/

}