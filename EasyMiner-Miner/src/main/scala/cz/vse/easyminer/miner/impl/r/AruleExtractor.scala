/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl.r

import cz.vse.easyminer.core.util.AnyToDouble
import cz.vse.easyminer.miner._
import cz.vse.easyminer.miner.impl.ItemText
import cz.vse.easyminer.preprocessing.AttributeDetail

/**
  * Created by Vaclav Zeman on 16. 11. 2015.
  */

object AruleExtractor {

  private class Cedent(attributes: Seq[AttributeDetail]) {
    private lazy val attributeMap = attributes.view.map(x => x.id -> x).toMap
    def unapply(str: String): Option[List[FixedValue]] = if (str.trim.isEmpty) {
      Some(Nil)
    } else {
      Some(str.trim.split(',').iterator.flatMap(ItemText.unapply).map(item => FixedValue(attributeMap(item.attribute), item.value)).toList)
    }
  }

  def getOutputARuleMapper(count: Count, attributes: Seq[AttributeDetail]) = {
    val ArulePattern = """\d*\s*"?\{(.*?)\}\s+=>\s+\{(.+?)\}"?(?:\s+|,)([0-9.]+)(?:\s+|,)([0-9.]+)(?:\s+|,)([0-9.]+)""".r
    val Cedent = new Cedent(attributes)
    val pf: PartialFunction[String, ARule] = {
      case ArulePattern(Cedent(ant), Cedent(con), AnyToDouble(s), AnyToDouble(c), AnyToDouble(l)) =>
        val (supp, conf, lift) = (Support(s), Confidence(c), Lift(l))
        ARule(
          ant,
          con,
          InterestMeasures(supp, conf, lift, MinRuleLength(ant.size), MaxRuleLength(ant.size + con.size)),
          ContingencyTable(supp, conf, lift, count)
        )
    }
    pf
  }

}