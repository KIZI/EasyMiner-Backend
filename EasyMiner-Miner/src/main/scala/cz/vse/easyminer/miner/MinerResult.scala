package cz.vse.easyminer.miner

import scala.concurrent.duration.Duration

/**
  * Created by propan on 21. 11. 2015.
  */
case class MinerResult(task: MinerTask, headers: Set[MinerResultHeader], rules: Seq[ARule])

sealed trait MinerResultHeader

sealed trait CriticalMinerResultHeader extends MinerResultHeader

object MinerResultHeader {

  case class Timeout(rulelen: Int) extends CriticalMinerResultHeader

  case class InternalLimit(rulelen: Int, size: Int) extends CriticalMinerResultHeader

  case class ExternalLimit(rulelen: Int, size: Int) extends CriticalMinerResultHeader

  case class MiningTime(preparing: Duration, mining: Duration, finishing: Duration) extends MinerResultHeader {
    def +(miningTime: MiningTime) = MiningTime(preparing + miningTime.preparing, mining + miningTime.mining, finishing + miningTime.finishing)
  }

  implicit class PimpedHeaderSet(originalHeaders: Set[MinerResultHeader]) {
    def merge(headers: Set[MinerResultHeader]): Set[MinerResultHeader] = headers.foldLeft(originalHeaders) {
      case (headers, x: MiningTime) => headers.collectFirst {
        case y: MiningTime => (headers - y) + (y + x)
      }.getOrElse(headers + x)
      case (headers, x) => headers + x
    }

    def merge(headers: MinerResultHeader*): Set[MinerResultHeader] = merge(headers.toSet)
  }

}