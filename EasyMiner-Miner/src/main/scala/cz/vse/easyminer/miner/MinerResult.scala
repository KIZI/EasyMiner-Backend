/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import scala.concurrent.duration.Duration

/**
  * Created by Vaclav Zeman on 21. 11. 2015.
  */

/**
  * Result from association rules mining
  *
  * @param task    miner task definition (input task)
  * @param headers meta information about mining
  * @param rules   mined assocation rules
  */
case class MinerResult(task: MinerTask, headers: Set[MinerResultHeader], rules: Seq[ARule])

/**
  * Meta information about mining
  */
sealed trait MinerResultHeader

sealed trait CriticalMinerResultHeader extends MinerResultHeader

object MinerResultHeader {

  /**
    * There was timeout during mining for a specific rule length
    *
    * @param rulelen rule length where timeout was thrown. Lower rule lengths were successfully mined
    */
  case class Timeout(rulelen: Int) extends CriticalMinerResultHeader

  /**
    * Stopping mining due to reaching of maximal rule size
    *
    * @param rulelen rule length where this event occured
    * @param size    mined rules size
    */
  case class InternalLimit(rulelen: Int, size: Int) extends CriticalMinerResultHeader

  /**
    * Stopping mining due to reaching of maximal rule size
    *
    * @param rulelen rule length where this event occured
    * @param size    mined rules size
    */
  case class ExternalLimit(rulelen: Int, size: Int) extends CriticalMinerResultHeader

  /**
    * Information about time of mining
    *
    * @param preparing preparing time before mining
    * @param mining    mining time only
    * @param finishing post processing time after mining
    */
  case class MiningTime(preparing: Duration, mining: Duration, finishing: Duration) extends MinerResultHeader {

    /**
      * Add time to this mining time
      *
      * @param miningTime delta time
      * @return new time
      */
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