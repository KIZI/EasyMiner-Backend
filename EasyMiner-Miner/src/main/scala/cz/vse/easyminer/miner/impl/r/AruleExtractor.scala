package cz.vse.easyminer.miner.impl.r

import cz.vse.easyminer.core.util.AnyToDouble
import cz.vse.easyminer.miner._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.InstanceTable
import cz.vse.easyminer.preprocessing.{AttributeDetail, NormalizedValue}

import scala.language.implicitConversions

/**
 * Created by propan on 16. 11. 2015.
 */

class AruleExtractor(attributes: Seq[AttributeDetail]) {

  private lazy val attributeMap = attributes.view.map(x => x.id -> x).toMap

  object RArule {

    implicit private def colNameToAttribute(colName: String): AttributeDetail = attributeMap(colName.trim.stripPrefix(InstanceTable.colNamePrefix).toInt)

    implicit private def valueToNormalizedValue(value: String): NormalizedValue = NormalizedValue(value.trim.toInt)

    def unapply(str: String) = if (str.trim.isEmpty) {
      Some(None, 0)
    } else {
      val values = str.trim.split(',').map { item =>
        val a = item.split('=')
        Value(FixedValue(a(0).trim, a(1).trim)).asInstanceOf[BoolExpression[FixedValue]]
      }
      values.reduceLeftOption(_ AND _).map(x => Some(x) -> values.length)
    }

  }

}

object AruleExtractor {

  def getOutputARuleMapper(count: Count, attributes: Seq[AttributeDetail]) = {
    type ARuleStruct = (String, String, Double, Double, Double)
    val ArulePattern = """\d*\s*"?\{(.*?)\}\s+=>\s+\{(.+?)\}"?(?:\s+|,)([0-9.]+)(?:\s+|,)([0-9.]+)(?:\s+|,)([0-9.]+)""".r
    val rAruleExtractor = new AruleExtractor(attributes)
    val pf: PartialFunction[String, ARule] = {
      case ArulePattern(rAruleExtractor.RArule(ant, antlen), rAruleExtractor.RArule(Some(con), conlen), AnyToDouble(s), AnyToDouble(c), AnyToDouble(l)) =>
        val (supp, conf, lift) = (Support(s), Confidence(c), Lift(l))
        ARule(
          ant,
          con,
          InterestMeasures(supp, conf, lift, MinRuleLength(antlen), MaxRuleLength(antlen + conlen)),
          ContingencyTable(supp, conf, lift, count)
        )
    }
    pf
  }

}