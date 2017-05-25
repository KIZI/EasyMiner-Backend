/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

trait Miner {

  self: MinerTaskValidator with MinerProcess =>

  final def mine(mt: MinerTask)(processListener: MinerResult => Unit): MinerResult = {
    validate(mt)
    process(mt)(processListener)
  }

}

trait MinerProcess {

  def process(mt: MinerTask)(processListener: MinerResult => Unit): MinerResult

}