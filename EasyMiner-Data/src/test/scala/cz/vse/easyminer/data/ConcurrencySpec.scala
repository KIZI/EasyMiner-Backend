package cz.vse.easyminer
package data

import java.util.concurrent.ConcurrentLinkedQueue

import cz.vse.easyminer.core.util.Concurrency._
import org.scalatest.{FlatSpec, Matchers}
import scalikejdbc.SQL

import scala.collection.JavaConversions._
import scala.concurrent.Future

/**
  * Created by propan on 1. 4. 2016.
  */
class ConcurrencySpec extends FlatSpec with Matchers {

  implicit val ec = actorSystem.dispatcher

  "DB connection pool" should "wait for closing all connections during destroying" in {
    val events = new ConcurrentLinkedQueue[Int]()
    val conn = DBSpec.makeMysqlConnector(false)
    val dbOps = Future {
      conn.DBConn.readOnly { implicit session =>
        events.add(1)
        SQL("SELECT 1 AS one").map(_.int("one")).first().apply()
        Thread.sleep(5000)
        events.add(5)
        SQL("SELECT 1 AS one").map(_.int("one")).first().apply()
      }
    }
    Thread.sleep(2000)
    events.add(2)
    conn.close()
    events.add(3)
    val conn2 = DBSpec.makeMysqlConnector(false)
    conn2.DBConn.readOnly { implicit session =>
      SQL("SELECT 1 AS one").map(_.int("one")).first().apply()
      events.add(4)
    }
    conn2.close()
    dbOps.quickResult
    events.toList shouldBe sorted
  }

}
