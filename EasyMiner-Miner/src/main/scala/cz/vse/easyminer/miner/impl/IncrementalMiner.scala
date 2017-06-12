/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.miner._
import org.slf4j.LoggerFactory
import BoolExpressionImpl._

import scala.annotation.tailrec

/**
  * Created by Vaclav Zeman on 27. 2. 2016.
  */

/**
  * This is abstraction for incremental mining of association rules
  * We sequentially mine rules with length 1-3, 4, 5, 6 etc...
  * For each length we return result therefore we can get result step by step
  */
trait IncrementalMiner {

  /**
    * Miner task definition
    */
  val minerTask: MinerTask
  /**
    * We send partial results to this method
    */
  val processListener: MinerResult => Unit
  /**
    * Total number of attributes
    */
  val numberOfAttributes: Int

  /**
    * Min rule length fetched from interest measures definition
    */
  lazy val minlen = minerTask.interestMeasures.minlen
  /**
    * Max rule length fetched from interest measures definition
    * Max rule length must not be greater than number of attributes
    */
  lazy val maxlen = {
    val maxlens = List(minerTask.antecedent, minerTask.consequent).map(_.map(_.toAttributeDetails.size).getOrElse(0))
    val uppermaxlen = if (maxlens.contains(0)) numberOfAttributes else maxlens.sum
    math.min(uppermaxlen, minerTask.interestMeasures.maxlen)
  }
  /**
    * Max mined rules fetched from interest measures definition
    */
  val limit = minerTask.interestMeasures.limit

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.impl.IncrementalMiner")

  /**
    * Mine rules with a specific rule length range
    *
    * @param minlen minimal rule length
    * @param maxlen maximal rule length
    * @return miner result which contains assocation rules
    */
  protected[this] def mine(minlen: Int, maxlen: Int): MinerResult

  /**
    * This function invokes mine function and then post-process a result
    * It sorts result, checks rules size and adds headers to result
    *
    * @param minlen         minimal rule length
    * @param maxlen         maximal rule length
    * @param totalRulesSize fetched number of rules from previous steps
    * @return miner result which contains assocation rules
    */
  def partialMine(minlen: Int, maxlen: Int, totalRulesSize: Int) = {
    val result = {
      val result = mine(minlen, maxlen)
      if (result.rules.size + totalRulesSize > limit) {
        MinerResult(
          minerTask,
          result.headers + MinerResultHeader.ExternalLimit(minerTask.interestMeasures.maxlen, result.rules.size + totalRulesSize),
          result.rules.sortBy(arule => (arule.interestMeasures.confidence, arule.interestMeasures.support))(Ordering.Tuple2[Double, Double].reverse).take(limit - totalRulesSize)
        )
      } else {
        result
      }
    }
    result.headers.foreach {
      case MinerResultHeader.Timeout(rulelen) => logger.debug(s"Timeout has been reached during mining process with rule length $rulelen")
      case MinerResultHeader.InternalLimit(rulelen, size) => logger.debug(s"Rules limit has been reached during mining process with rule length $rulelen and size $size, limit is $limit")
      case MinerResultHeader.ExternalLimit(rulelen, size) => logger.debug(s"Rules limit has been reached out of mining process with rule length $rulelen and size $size, limit is $limit")
      case _ =>
    }
    result
  }

  /**
    * This sequentially mines rules with length 1-3, 4, 5, 6 etc...
    * This sends partial results to processListener and merges all together
    *
    * @return miner result which contains assocation rules
    */
  def incrementalMine = {
    val ruleLengths: Iterator[Int] = (minlen to maxlen).iterator
    @tailrec
    def nextMine(result: MinerResult = MinerResult(minerTask, Set.empty, Nil)): MinerResult = if (ruleLengths.hasNext) {
      val ruleLength = ruleLengths.next()
      val partialResult = if (ruleLength == minlen && minlen <= 3) {
        val maxrulelength = if (maxlen > 3) 3 else maxlen
        partialMine(ruleLength, maxrulelength, result.rules.size)
      } else if (ruleLength > 3) {
        partialMine(ruleLength, ruleLength, result.rules.size)
      } else {
        MinerResult(minerTask, Set.empty, Nil)
      }
      if (partialResult.rules.nonEmpty) {
        processListener(partialResult)
      }
      val mergedResult = MinerResult(minerTask, result.headers merge partialResult.headers, result.rules ++ partialResult.rules)
      if (mergedResult.headers.exists(_.isInstanceOf[CriticalMinerResultHeader]))
        mergedResult
      else
        nextMine(mergedResult)
    } else {
      result
    }
    nextMine()
  }

}