package cz.vse.easyminer.miner.impl

import java.util.Date

import cz.vse.easyminer.core.util.{Conf, Template}
import cz.vse.easyminer.miner.{BorrowedConnection, RConnectionPool}
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

class RConnectionPoolImpl(rServer: String, rPort: Int, prepareLibs: Boolean = true) extends RConnectionPool {

  import ExecutionContext.Implicits.global

  val maxIdle = 10
  val minIdle = 2
  val connectionTimeout = 15 minutes
  val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.impl.RConnectionPool")

  private lazy val rInitScript = Template("RInit.mustache").trim.replaceAll("\r\n", "\n")

  //priority queue
  //older connections have higher priority
  private val pool = new collection.mutable.PriorityQueue[BorrowedConnection]()(new Ordering[BorrowedConnection] {
    def compare(a: BorrowedConnection, b: BorrowedConnection) = b.created compare a.created
  })

  private var activeConnections = 0

  private def createConnection = {
    val conn = new BorrowedConnection(rServer, rPort)
    if (prepareLibs) {
      conn.eval("options(java.parameters=\"-Xmx" + Conf().get[String]("easyminer.miner.r.java-max-heap-size") + "\")")
      conn.eval("library(RJDBC)")
      conn.eval("library(arules)")
      conn.eval("library(rCBA)")
      conn.eval("library(R.utils)")
      conn.parseAndEval(rInitScript)
    }
    logger.debug("New R connection has been created and prepared.")
    conn
  }

  def numActive = activeConnections

  def numIdle = pool.size

  def borrow = {
    //borrow connection from pool
    //returns none if the pool is empty
    val connOpt = pool.synchronized {
      activeConnections = activeConnections + 1
      try {
        Some(pool.dequeue())
      } catch {
        case _: NoSuchElementException => None
      }
    }
    //get connection
    //if connection is none then create new connection
    //this is potentially expensive operation and it should not block any other pooling methods!
    val conn = connOpt.getOrElse(createConnection)
    //call postborrow method asynchronously
    Future {
      postBorrow()
    }
    //return connection
    logger.debug("R connection has been borrowed. Connection creation time: " + new Date(conn.created))
    conn
  }

  def release(bc: BorrowedConnection) = {
    Future {
      //check whether connection pool is not full
      val poolIsNotFull = pool.synchronized {
        activeConnections = activeConnections - 1
        pool.size < maxIdle
      }
      //close connection
      bc.close()
      if (poolIsNotFull) {
        //if connection pool is not full then create new connection which replaces old closed connection.
        //this is expensive operation and it should not block any other pooling methods!
        val conn = createConnection
        pool.synchronized {
          //if pool is not full then add the new connection to the pool otherwise close connection
          if (pool.size < maxIdle) {
            pool.enqueue(conn)
          } else {
            conn.close()
          }
        }
      }
    }
    logger.debug("R connection has been released.")
  }

  private def postBorrow() = this.synchronized {
    //postborrow method is synchronized method to prevent creation of a lot of connections
    //create connections to fill minIdle space in the pool
    //this is potentially expensive operation and it should not block any other pooling methods!
    val conns = (0 until (minIdle - pool.size)).map(_ => createConnection)
    pool.synchronized {
      //add created connections to the pool or close it if the min idle threshold has been reached
      for (conn <- conns) {
        if (pool.size < minIdle) pool.enqueue(conn) else conn.close()
      }
      //if the pool is still too small then add connections synchronously
      //this is blocking and expensive operation but less likely!
      while (pool.size < minIdle) {
        pool.enqueue(createConnection)
      }
    }
  }

  def refresh() = pool.synchronized {
    //close old connections
    //the connection pool may be empty if there is no connection borrowing within connectionTimeout
    while (pool.headOption.exists(_.created < System.currentTimeMillis - connectionTimeout.toMillis)) {
      pool.dequeue().close
    }
    if (numActive + numIdle > 0) {
      logger.debug(s"R connection pool has been refreshed. Current state is: active = $numActive, idle = $numIdle")
    }
  }

  def close() = pool.synchronized {
    pool.dequeueAll foreach (_.close)
  }

}

object RConnectionPoolImpl {

  val default = new RConnectionPoolImpl(Conf().get[String]("easyminer.miner.r.rserve-address"), Conf().getOrElse[Int]("easyminer.miner.r.rserve-port", 6311))

}