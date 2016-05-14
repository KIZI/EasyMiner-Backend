package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.util.BasicFunction.mergeMaps
import cz.vse.easyminer.core.util.Template
import cz.vse.easyminer.data.{NullValue, NumericValue, NominalValue}
import cz.vse.easyminer.miner._
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing.{DatasetType, AttributeDetail, NormalizedValue, ValueMapperOps}
import spray.http.{HttpCharsets, ContentType}
import spray.http.MediaTypes._
import spray.httpx.marshalling.Marshaller

import scala.language.implicitConversions

class PmmlResult(data: MinerResult, valueMapperOps: ValueMapperOps) {

  self: ARuleVisualizer with BoolExpressionVisualizer[Attribute] =>

  val MappedFixedValue: MappedFixedValue = {
    def boolExpToValueMap(boolExpression: BoolExpression[FixedValue]): Map[AttributeDetail, Set[NormalizedValue]] = boolExpression match {
      case ANDOR(x, y) => mergeMaps(boolExpToValueMap(x), boolExpToValueMap(y))
      case NOT(x) => boolExpToValueMap(x)
      case Value(x) => Map(x.attributeDetail -> Set(x.normalizedValue))
    }
    valueMapperOps.normalizedValueMapper(
      data.rules.iterator.map(x => (x.consequent :: x.antecedent.toList).reduce(_ AND _)).map(boolExpToValueMap).reduceOption((x1, x2) => mergeMaps(x1, x2)).getOrElse(Map.empty)
    )
  }

  private val arulesToPMMLMapper: PartialFunction[ARule, Map[String, Any]] = {
    case ar @ ARule(ant, con, im, ct) => Map(
      "id" -> s"AR${ar.hashCode}",
      "id-consequent" -> baref(con),
      "text" -> aruleToString(ar),
      "a" -> ct.a,
      "b" -> ct.b,
      "c" -> ct.c,
      "d" -> ct.d
    ) ++ ant.map(x => "id-antecedent" -> baref(x)).toMap
  }

  private val dbaToPMMLMapper = {
    def makeDbaMap(expr: BoolExpression[FixedValue], children: List[String]): Map[String, Any] = Map(
      "id" -> baref(expr),
      "text" -> exprToString(expr),
      "barefs" -> children
    )
    val pf: PartialFunction[BoolExpression[FixedValue], Map[String, Any]] = {
      case e @ ANDOR(x, y) => makeDbaMap(e, List(baref(x), baref(y)))
      case e @ NOT(x) => makeDbaMap(e, List(baref(x)))
    }
    pf
  }

  private val bbaToPMMLMapper: PartialFunction[BoolExpression[FixedValue], Map[String, Any]] = {
    case e @ MappedFixedValue(attribute, value) => Map(
      "id" -> baref(e),
      "text" -> exprToString(e),
      "name" -> attribute.name,
      "value" -> (value match {
        case NominalValue(value) => value
        case NumericValue(value) => value.toString
        case NullValue => "null"
      })
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

  def toPMML = {
    val exprs = data.rules.iterator.flatMap(x => x.consequent :: x.antecedent.toList).map(collectExpression).reduceOption(_ ++ _)
    Template.apply(
      "PMMLResult.template.mustache",
      Map(
        "arules" -> data.rules.collect(arulesToPMMLMapper),
        "dbas" -> exprs.getOrElse(Nil).collect(dbaToPMMLMapper),
        "bbas" -> exprs.getOrElse(Nil).collect(bbaToPMMLMapper),
        "number-of-rules" -> data.rules.size,
        "has-headers" -> data.headers.nonEmpty,
        "headers" -> data.headers.flatMap {
          case MinerResultHeader.Timeout(rulelen) => List(Map("name" -> "timeout-with-rulelength", "value" -> rulelen))
          case MinerResultHeader.InternalLimit(rulelen, size) => List(Map("name" -> "internallimit-with-rulelength", "value" -> rulelen), Map("name" -> "internallimit-with-size", "value" -> size))
          case MinerResultHeader.ExternalLimit(rulelen, size) => List(Map("name" -> "externallimit-with-rulelength", "value" -> rulelen), Map("name" -> "externallimit-with-size", "value" -> size))
        }
      )
    ).trim
  }

}

object PmmlResult {

  implicit def minerResultToPmmlResult(minerResult: MinerResult)(implicit datasetTypeConv: DatasetType => DatasetTypeOps[DatasetType]): PmmlResult = {
    new PmmlResult(minerResult, minerResult.task.datasetDetail.`type`.toValueMapperOps(minerResult.task.datasetDetail)) with ARuleText with BoolExpressionShortText
  }

  implicit def pmmlResultMarshaller: Marshaller[PmmlResult] = {
    Marshaller.delegate[PmmlResult, String](ContentType(`application/xml`, HttpCharsets.`UTF-8`)) { pmmlResult =>
      pmmlResult.toPMML
    }
  }

}