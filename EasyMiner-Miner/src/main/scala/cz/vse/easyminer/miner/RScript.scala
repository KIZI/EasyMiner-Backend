/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import org.rosuda.REngine.REXPMismatchException
import org.rosuda.REngine.Rserve.RConnection

/**
  * Simple class for running R script from scala
  *
  * @param conn R connection to R environment by RServe
  */
class RScript private(conn: RConnection) {

  private def normalizeScript(rscript: String) = rscript.trim.replaceAll("\r\n", "\n")

  /**
    * Run an R script and return lines of result
    *
    * @param rscript R script
    * @return result lines returned from R
    */
  def eval(rscript: String): Array[String] = {
    val result = conn.parseAndEval(normalizeScript(rscript))
    if (result == null) {
      Array()
    } else {
      try {
        result.asStrings()
      } catch {
        case ex: REXPMismatchException => Array()
      }
    }
  }

}

object RScript {

  /**
    * Run R script
    *
    * @param rscript R script
    * @param rcp     R connection pool
    * @return result lines returned from R
    */
  def eval(rscript: String)(implicit rcp: RConnectionPool) = evalTx { r =>
    r.eval(rscript)
  }

  /**
    * Run block "f" where we can sequentially execute R scripts.
    * R connection is automatically closed after this "f" block
    *
    * @param f   block of R scripts
    * @param rcp R connection pool
    * @tparam T type of result
    * @return result of R block
    */
  def evalTx[T](f: RScript => T)(implicit rcp: RConnectionPool): T = {
    val conn = rcp.borrow
    try {
      f(new RScript(conn))
    } finally {
      rcp.release(conn)
    }
  }

}