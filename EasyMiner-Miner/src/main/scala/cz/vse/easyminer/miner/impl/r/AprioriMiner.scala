package cz.vse.easyminer.miner.impl.r

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.Conf
import cz.vse.easyminer.miner._

/**
 * Created by propan on 19. 11. 2015.
 */
class AprioriMiner(val r: RScript)(implicit val mysqlDBConnector: MysqlDBConnector) extends Miner with AprioriMinerProcess {

  self: MinerTaskValidator =>

  val jdbcDriverAbsolutePath = Conf().get[String]("easyminer.miner.r.jdbc-driver-dir-absolute-path")

}