package cz.vse.easyminer.miner

/**
 * Created by propan on 21. 11. 2015.
 */
case class MinerResult(task: MinerTask, headers: Set[MinerResultHeader], rules: Seq[ARule])

sealed trait MinerResultHeader

sealed trait CriticalMinerResultHeader

object MinerResultHeader {

  case class Timeout(rulelen: Int) extends MinerResultHeader with CriticalMinerResultHeader

  case class InternalLimit(rulelen: Int, size: Int) extends MinerResultHeader with CriticalMinerResultHeader

  case class ExternalLimit(rulelen: Int, size: Int) extends MinerResultHeader with CriticalMinerResultHeader

}