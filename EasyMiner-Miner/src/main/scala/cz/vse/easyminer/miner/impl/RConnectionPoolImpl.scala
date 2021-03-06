/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import java.util.Date

import cz.vse.easyminer.core.util.{Conf, Template}
import cz.vse.easyminer.miner.{BorrowedConnection, RConnectionPool}
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Connection pool for connections to R environment by RServe.
  * All operations within this connection pool are thread safe.
  * If in the pool is a prepared connection then after calling borrow function the connection is fetched from pool and there is not created a new one (it saves time)
  * After close connection, the connection is throw away and new one is created and saved in the pool. It waits for borrowing
  * If a connection is in the connection pool more than 15 minutes, it is automatically closed
  *
  * @param rServer     RServe address
  * @param rPort       RServe port
  * @param prepareLibs if this flag is true then after each connection all necesary libraries for assotiation rules mining are loaded
  */
class RConnectionPoolImpl(rServer: String, rPort: Int, prepareLibs: Boolean = true) extends RConnectionPool {

  import ExecutionContext.Implicits.global

  /**
    * Max idle connections in the pool
    */
  val maxIdle = 10
  /**
    * Min idle connections in the pool
    */
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

  /**
    * Get number of active/borrowed connections
    *
    * @return number of active connections
    */
  def numActive = activeConnections

  /**
    * Get number of idle connections in the pool
    *
    * @return number of idle connections
    */
  def numIdle = pool.size


  /**
    * Get R connection from pool
    *
    * @return R connection
    */
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

  /**
    * Release R connection and return it back into the pool
    *
    * @param bc R connection
    */
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

  /**
    * Refresh all connections in pool (close old idle connections)
    */
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

  /**
    * Close all R connections from the connection pool
    */
  def close() = pool.synchronized {
    pool.dequeueAll foreach (_.close)
  }

}

object RConnectionPoolImpl {

  /**
    * Default R connection pool
    */
  val default = new RConnectionPoolImpl(Conf().get[String]("easyminer.miner.r.rserve-address"), Conf().getOrElse[Int]("easyminer.miner.r.rserve-port", 6311))

}