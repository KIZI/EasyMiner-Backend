/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import org.rosuda.REngine.REXPMismatchException
import org.rosuda.REngine.Rserve.RConnection

class RScript private(conn: RConnection) {

  private def normalizeScript(rscript: String) = rscript.trim.replaceAll("\r\n", "\n")

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

  def eval(rscript: String)(implicit rcp: RConnectionPool) = evalTx { r =>
    r.eval(rscript)
  }

  def evalTx[T](f: RScript => T)(implicit rcp: RConnectionPool): T = {
    val conn = rcp.borrow
    try {
      f(new RScript(conn))
    } finally {
      rcp.release(conn)
    }
  }

}