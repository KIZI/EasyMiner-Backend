/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import org.rosuda.REngine.Rserve.RConnection

/**
  * Abstraction for connection pool of connections to R environment by RServe
  */
trait RConnectionPool {

  /**
    * Get R connection from pool
    *
    * @return R connection
    */
  def borrow: BorrowedConnection

  /**
    * Release R connection and return it back into the pool
    *
    * @param bc R connection
    */
  def release(bc: BorrowedConnection)

  /**
    * Refresh all connections in pool (close old idle connections)
    */
  def refresh()

  /**
    * Close all R connections from the connection pool
    */
  def close()
}

trait RConnectionInit {
  def init(rConnection: RConnection)
}

class BorrowedConnection(rServer: String, rPort: Int) extends RConnection(rServer, rPort) {
  val created = System.currentTimeMillis
}