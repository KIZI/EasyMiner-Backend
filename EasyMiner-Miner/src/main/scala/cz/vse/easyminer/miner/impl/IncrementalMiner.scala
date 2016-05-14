package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.miner._
import cz.vse.easyminer.preprocessing.AttributeDetail
import org.slf4j.LoggerFactory

import scala.annotation.tailrec

/**
 * Created by propan on 27. 2. 2016.
 */
trait IncrementalMiner {

  val minerTask: MinerTask
  val processListener: MinerResult => Unit
  val attributes: Seq[AttributeDetail]

  lazy val minlen = minerTask.interestMeasures.minlen
  lazy val maxlen = {
    def boolExpressionToAttributeSet(boolExpression: BoolExpression[Attribute]): Option[Set[AttributeDetail]] = boolExpression match {
      case a ANDOR b => boolExpressionToAttributeSet(a).flatMap(a => boolExpressionToAttributeSet(b).map(_ ++ a))
      case NOT(a) => None
      case Value(FixedValue(a, _)) => Some(Set(a))
      case Value(AllValues(a)) => Some(Set(a))
      case _ => None
    }
    val uppermaxlen = minerTask.antecedent.flatMap(ant => minerTask.consequent.map(ant.AND)).flatMap(boolExpressionToAttributeSet).map(_.size).getOrElse(attributes.size)
    math.min(uppermaxlen, minerTask.interestMeasures.maxlen)
  }
  val limit = minerTask.interestMeasures.limit

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.impl.IncrementalMiner")

  protected[this] def mine(minlen: Int, maxlen: Int): MinerResult

  def partialMine(minlen: Int, maxlen: Int, totalRulesSize: Int) = {
    val result = {
      val result = mine(minlen, maxlen)
      if (result.rules.size + totalRulesSize > limit) {
        MinerResult(
          minerTask,
          result.headers + MinerResultHeader.ExternalLimit(minerTask.interestMeasures.maxlen, result.rules.size + totalRulesSize),
          result.rules.view.sortBy(arule => (arule.interestMeasures.confidence, arule.interestMeasures.support))(Ordering.Tuple2[Double, Double].reverse).take(limit - totalRulesSize)
        )
      } else {
        result
      }
    }
    result.headers.foreach {
      case MinerResultHeader.Timeout(rulelen) => logger.debug(s"Timeout has been reached during mining process with rule length $rulelen")
      case MinerResultHeader.InternalLimit(rulelen, size) => logger.debug(s"Rules limit has been reached during mining process with rule length $rulelen and size $size, limit is $limit")
      case MinerResultHeader.ExternalLimit(rulelen, size) => logger.debug(s"Rules limit has been reached out of mining process with rule length $rulelen and size $size, limit is $limit")
    }
    result
  }


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
      val mergedResult = MinerResult(minerTask, result.headers ++ partialResult.headers, result.rules ++ partialResult.rules)
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
