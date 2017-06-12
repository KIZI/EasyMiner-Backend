/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

/**
  * Main abstraction for mining association rules
  */
trait Miner {

  self: MinerTaskValidator with MinerProcess =>

  /**
    * Mine rules from a dataset by a miner task definition
    *
    * @param mt              task definition for mining asssocation rules
    * @param processListener listener that obtains partial results of association rules
    * @return completed result which contains all mined association rules
    */
  final def mine(mt: MinerTask)(processListener: MinerResult => Unit): MinerResult = {
    validate(mt)
    process(mt)(processListener)
  }

}

trait MinerProcess {

  /**
    * Mine rules from a dataset by a miner task definition
    *
    * @param mt              task definition for mining asssocation rules
    * @param processListener listener that obtains partial results of association rules
    * @return completed result which contains all mined association rules
    */
  def process(mt: MinerTask)(processListener: MinerResult => Unit): MinerResult

}