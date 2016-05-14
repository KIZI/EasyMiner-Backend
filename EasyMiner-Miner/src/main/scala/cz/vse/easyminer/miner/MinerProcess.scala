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