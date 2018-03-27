package cz.vse.easyminer.miner.impl.spark

import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.miner.{MinerTaskValidator, Miner}

/**
 * Created by propan on 1. 3. 2016.
 */
class FpGrowthMiner(implicit val hiveDBConnector: HiveDBConnector, implicit val mysqlDBConnector: MysqlDBConnector) extends Miner with FpGrowthMinerProcess {

  self: MinerTaskValidator =>

}