package cz.vse.easyminer.miner.impl.r

import cz.vse.easyminer.core.util.{AnyToInt, Template}
import cz.vse.easyminer.miner.MinerResultHeader.MiningTime
import cz.vse.easyminer.miner._
import cz.vse.easyminer.miner.impl.IncrementalMiner
import cz.vse.easyminer.preprocessing.AttributeDetail
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.language.postfixOps

/**
  * Created by propan on 27. 2. 2016.
  */
private[r] class AprioriIncrementalMiner(val minerTask: MinerTask, attributes: Seq[AttributeDetail])(val processListener: (MinerResult) => Unit)(implicit r: RScript) extends IncrementalMiner {

  val numberOfAttributes: Int = attributes.size

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.impl.r.AprioriIncrementalMiner")
  private val rProcessTemplateName = "RAprioriProcess.mustache"

  private val outputAruleMapper = AruleExtractor.getOutputARuleMapper(Count(minerTask.datasetDetail.size), attributes)

  private val outputHeaderMapper = {
    val TimeoutRegExp = """timeout rulelen=(\d+)""".r.unanchored
    val LimitRegExp = """limit rulelen=(\d+) size=(\d+)""".r.unanchored
    //val CbaLimitRegExp = """cbalimit size=(\d+)""".r.unanchored
    val pf: PartialFunction[String, MinerResultHeader] = {
      case TimeoutRegExp(AnyToInt(maxlen)) => MinerResultHeader.Timeout(maxlen)
      case LimitRegExp(AnyToInt(maxlen), AnyToInt(size)) => MinerResultHeader.InternalLimit(maxlen, size)
    }
    pf
  }

  protected[this] def mine(minlen: Int, maxlen: Int): MinerResult = {
    val startTime = System.currentTimeMillis()
    val im = Map(
      "minlen" -> minlen,
      "maxlen" -> maxlen,
      "auto" -> minerTask.interestMeasures.has(Auto)
    )
    val rscript = Template(
      rProcessTemplateName,
      im
    )
    logger.trace("This Rscript will be passed to the Rserve:\n" + rscript)
    val result = r.eval(rscript)
    val timeMining = (System.currentTimeMillis() - startTime) millis
    val headers = if (result.nonEmpty) {
      result.view.tail.takeWhile(outputHeaderMapper.isDefinedAt).map(outputHeaderMapper).toSet
    } else {
      Set.empty[MinerResultHeader]
    }
    val rules = result.iterator.collect(outputAruleMapper).toList
    logger.debug(s"Number of found association rules $im: ${rules.size}")
    val timeFinishing = ((System.currentTimeMillis() - startTime) millis) - timeMining
    MinerResult(minerTask, headers merge MiningTime(Duration.Zero, timeMining, timeFinishing), rules)
  }

}