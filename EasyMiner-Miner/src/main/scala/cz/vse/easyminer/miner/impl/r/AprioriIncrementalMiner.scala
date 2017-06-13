/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

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
  * Created by Vaclav Zeman on 27. 2. 2016.
  */

/**
  * Incremental miner which uses apriori algorithm implemented in the arules R library
  *
  * @param minerTask       miner task definitions
  * @param attributes      all dataset attributes
  * @param processListener function to which partial results are being sent
  * @param r               implicit! r script executor
  */
private[r] class AprioriIncrementalMiner(val minerTask: MinerTask, attributes: Seq[AttributeDetail])(val processListener: (MinerResult) => Unit)(implicit r: RScript) extends IncrementalMiner {

  val numberOfAttributes: Int = attributes.size

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.impl.r.AprioriIncrementalMiner")
  /**
    * Mustache template with R mining function for partial mining
    */
  private val rProcessTemplateName = "RAprioriProcess.mustache"
  /**
    * Mapper for conversion of association rules mined from R arules library into ARule objects
    */
  private val outputAruleMapper = AruleExtractor.getOutputARuleMapper(Count(minerTask.datasetDetail.size), attributes)
  /**
    * Mapper for conversion of headers (meta information) about mining in R into scala objects
    */
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

  /**
    * Mine rules with a specific rule length range
    *
    * @param minlen minimal rule length
    * @param maxlen maximal rule length
    * @return miner result which contains assocation rules
    */
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