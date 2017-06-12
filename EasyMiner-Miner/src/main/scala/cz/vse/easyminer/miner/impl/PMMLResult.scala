/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.util.Template
import cz.vse.easyminer.data.NominalValue
import cz.vse.easyminer.miner._
import cz.vse.easyminer.miner.conversion.AttributeConversion.PimpedFixedValueSeq
import cz.vse.easyminer.miner.conversion.BoolExpressionConversion.fixedValuesToBoolExpression
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing.{AttributeDetail, DatasetType, ValueMapperOps}
import spray.http.MediaTypes._
import spray.http.{ContentType, HttpCharsets}
import spray.httpx.marshalling.Marshaller

import scala.language.implicitConversions

/**
  * Write the result of association rules mining into PMML document
  * PMML contains all rules with contingency tables, attributes and values information and headers as extensions with mining meta-information
  * Final PMML is created according to mustache template PMMLResult.template.mustache
  *
  * @param data           result of association rules mining
  * @param valueMapperOps object which maps value IDs to text values and vice versa$
  */
class PmmlResult(data: MinerResult, valueMapperOps: ValueMapperOps) {

  self: ARuleVisualizer with BoolExpressionVisualizer[Attribute] =>

  private val startTime = System.currentTimeMillis()

  val MappedFixedValue: MappedFixedValue = {
    def fixedValuesToValueMap(fixedValues: Seq[FixedValue]): Map[AttributeDetail, Set[Int]] = fixedValues.groupBy(_.attributeDetail).mapValues(_.map(_.item).toSet)
    valueMapperOps.itemMapper(
      fixedValuesToValueMap(data.rules.flatMap(x => x.consequent ::: x.antecedent))
    )
  }

  /**
    * Map assotiation rule to template parameters
    */
  private val arulesToPMMLMapper: PartialFunction[ARule, Map[String, Any]] = {
    case ar@ARule(ant, con, im, ct) => Map(
      "id" -> s"AR${ar.hashCode}",
      "id-consequent" -> baref(con),
      "text" -> aruleToString(ar),
      "a" -> ct.a,
      "b" -> ct.b,
      "c" -> ct.c,
      "d" -> ct.d
    ) ++ (if (ant.isEmpty) Map.empty else Map("id-antecedent" -> baref(ant)))
  }

  /**
    * Map rule statement to template parameters
    */
  private val dbaToPMMLMapper = {
    def makeDbaMap(expr: BoolExpression[FixedValue], children: List[String]): Map[String, Any] = Map(
      "id" -> baref(expr),
      "text" -> exprToString(expr),
      "barefs" -> children
    )
    val pf: PartialFunction[BoolExpression[FixedValue], Map[String, Any]] = {
      case e@ANDOR(x, y) => makeDbaMap(e, List(baref(x), baref(y)))
      case e@NOT(x) => makeDbaMap(e, List(baref(x)))
    }
    pf
  }

  /**
    * Map rule atom to template parameters
    */
  private val bbaToPMMLMapper: PartialFunction[BoolExpression[FixedValue], Map[String, Any]] = {
    case e@MappedFixedValue(attribute, NominalValue(value)) => Map(
      "id" -> baref(e),
      "text" -> exprToString(e),
      "name" -> attribute.name,
      "value" -> value
    )
  }

  private def baref(expr: BoolExpression[FixedValue]) = expr match {
    case Value(_) => s"BBA${expr.hashCode}"
    case _ => s"DBA${expr.hashCode}"
  }

  private def collectExpression(be: BoolExpression[FixedValue]): Set[BoolExpression[FixedValue]] = be match {
    case ANDOR(x, y) => (collectExpression(x) ++ collectExpression(y)) + be
    case NOT(x) => collectExpression(x) + be
    case _ => Set(be)
  }

  /**
    * Create PMML document from mining result
    *
    * @return PMML as a string
    */
  def toPMML = {
    val exprs = data.rules.iterator
      .flatMap[BoolExpression[FixedValue]](x => x.consequent :: x.antecedent.toOptBoolExpression.map(x => List(x)).getOrElse(Nil))
      .map(collectExpression)
      .reduceOption(_ ++ _)
      .getOrElse(Set.empty)
    val pmmlMap = Map(
      "arules" -> data.rules.collect(arulesToPMMLMapper),
      "dbas" -> exprs.collect(dbaToPMMLMapper),
      "bbas" -> exprs.collect(bbaToPMMLMapper),
      "number-of-rules" -> data.rules.size,
      "has-headers" -> data.headers.nonEmpty
    )
    val headers = data.headers.flatMap {
      case MinerResultHeader.Timeout(rulelen) => List(Map("name" -> "timeout-with-rulelength", "value" -> rulelen))
      case MinerResultHeader.InternalLimit(rulelen, size) => List(Map("name" -> "internallimit-with-rulelength", "value" -> rulelen), Map("name" -> "internallimit-with-size", "value" -> size))
      case MinerResultHeader.ExternalLimit(rulelen, size) => List(Map("name" -> "externallimit-with-rulelength", "value" -> rulelen), Map("name" -> "externallimit-with-size", "value" -> size))
      case MinerResultHeader.MiningTime(preparing, mining, finishing) => List(Map("name" -> "pre-mining-time", "value" -> preparing.toMillis), Map("name" -> "mining-time", "value" -> mining.toMillis), Map("name" -> "post-mining-time", "value" -> (finishing.toMillis + System.currentTimeMillis() - startTime)))
    }
    Template.apply(
      "PMMLResult.template.mustache",
      pmmlMap + ("headers" -> headers)
    ).trim
  }

}

object PmmlResult {

  /**
    * Implicit conversion for miner result to pmml result object
    *
    * @param minerResult     miner result
    * @param datasetTypeConv implicit! dataset type conversion to dataset type operations object
    * @return pmml result object
    */
  implicit def minerResultToPmmlResult(minerResult: MinerResult)(implicit datasetTypeConv: DatasetType => DatasetTypeOps[DatasetType]): PmmlResult = {
    new PmmlResult(minerResult, minerResult.task.datasetDetail.`type`.toValueMapperOps(minerResult.task.datasetDetail)) with ARuleText with BoolExpressionShortText
  }

  /**
    * Converts pmml result to HTTP response as XML
    *
    * @return PMML converter
    */
  implicit def pmmlResultMarshaller: Marshaller[PmmlResult] = {
    Marshaller.delegate[PmmlResult, String](ContentType(`application/xml`, HttpCharsets.`UTF-8`)) { pmmlResult =>
      pmmlResult.toPMML
    }
  }

}