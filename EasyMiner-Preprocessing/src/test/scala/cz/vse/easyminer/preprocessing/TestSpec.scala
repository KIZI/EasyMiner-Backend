package cz.vse.easyminer.preprocessing

import java.sql.SQLIntegrityConstraintViolationException

import cz.vse.easyminer.core.PersistentLock
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.impl.db.mysql.Tables.DataSourceTable
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import scalikejdbc._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Random

/**
 * Created by propan on 16. 2. 2016.
 */
class TestSpec extends FlatSpec with Matchers with TemplateOpt with BeforeAndAfterAll with MysqlDataDbOps {

  val mysqlDBConnector: MysqlDBConnector = DBSpec.makeMysqlConnector(true)

  implicit object LockTable extends SQLSyntaxSupport[PersistentLock] {
    override def columns: Seq[String] = List("name", "refresh_time")
  }

  import mysqlDBConnector._

  import ExecutionContext.Implicits._

  "neco" should "neco" ignore {
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
  }

}