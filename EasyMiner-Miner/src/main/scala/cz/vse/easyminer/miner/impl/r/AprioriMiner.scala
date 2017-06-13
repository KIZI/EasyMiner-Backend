/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl.r

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.Conf
import cz.vse.easyminer.miner._

/**
  * Created by Vaclav Zeman on 19. 11. 2015.
  */

/**
  * Main class for apriori mining within R environment
  *
  * @param r                r script executor
  * @param mysqlDBConnector mysql database connection
  */
class AprioriMiner(val r: RScript)(implicit val mysqlDBConnector: MysqlDBConnector) extends Miner with AprioriMinerProcess {

  self: MinerTaskValidator =>

  /**
    * path to java jdbc driver file for RJDBC library in R
    */
  val jdbcDriverAbsolutePath = Conf().get[String]("easyminer.miner.r.jdbc-driver-dir-absolute-path")

}