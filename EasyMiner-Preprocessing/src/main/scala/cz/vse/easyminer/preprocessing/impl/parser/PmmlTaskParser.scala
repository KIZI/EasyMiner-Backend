package cz.vse.easyminer.preprocessing.impl.parser

import cz.vse.easyminer.core.util.{AnyToDouble, AnyToInt}
import cz.vse.easyminer.core.util.XmlUtils.TrimmedXml
import cz.vse.easyminer.data.{ExclusiveIntervalBorder, InclusiveIntervalBorder}
import cz.vse.easyminer.preprocessing._

/**
  * Created by propan on 2. 2. 2016.
  */
class PmmlTaskParser(pmml: TrimmedXml) extends TaskParser {

  case class DerivedField(name: String, node: xml.Node)

  private val simpleAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = (x.node \ "MapValues" \ "FieldColumnPair").headOption.exists(_.child.isEmpty)

    def apply(v1: DerivedField): Attribute = {
      val fieldId = AnyToInt.unapply(v1.node \ "MapValues" \ "FieldColumnPair" \@ "field").getOrElse(0)
      SimpleAttribute(v1.name, fieldId)
    }
  }

  private val nominalEnumerationAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = (x.node \ "MapValues" \ "FieldColumnPair").headOption.exists(_.child.isEmpty) && (x.node \ "MapValues" \ "InlineTable" \ "row").nonEmpty

    def apply(v1: DerivedField): Attribute = {
      val fieldId = AnyToInt.unapply(v1.node \ "MapValues" \ "FieldColumnPair" \@ "field").getOrElse(0)
      val mapping = (v1.node \ "MapValues" \ "InlineTable" \ "row").collect {
        case <row><column>{xml.Text(originalValue)}</column><field>{xml.Text(targetValue)}</field></row> => targetValue -> originalValue
      }.groupBy(_._1).iterator.map(x => NominalEnumerationAttribute.Bin(x._1, x._2.map(_._2))).toList
      NominalEnumerationAttribute(v1.name, fieldId, mapping)
    }
  }

  private val numericIntervalsAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = (x.node \ "Discretize" \ "DiscretizeBin").nonEmpty

    def apply(v1: DerivedField): Attribute = {
      val fieldId = AnyToInt.unapply(v1.node \ "Discretize" \@ "field").getOrElse(0)
      val intervals = (v1.node \ "Discretize" \ "DiscretizeBin").flatMap { discretizeBinNode =>
        val binValue = discretizeBinNode \@ "binValue"
        (discretizeBinNode \ "Interval").map(intervalNodeToInterval).map(binValue -> _)
      }.groupBy(_._1).iterator.map(x => NumericIntervalsAttribute.Bin(x._1, x._2.map(_._2))).toList.sortBy(_.intervals.minBy(_.from.value).from.value)
      NumericIntervalsAttribute(v1.name, fieldId, intervals)
    }

    private def intervalNodeToInterval(intervalNode: xml.Node) = {
      val from = AnyToDouble.unapply(intervalNode \@ "leftMargin").getOrElse(Double.NegativeInfinity)
      val to = AnyToDouble.unapply(intervalNode \@ "rightMargin").getOrElse(Double.PositiveInfinity)
      intervalNode \@ "closure" match {
        case "openClosed" => NumericIntervalsAttribute.Interval(ExclusiveIntervalBorder(from), InclusiveIntervalBorder(to))
        case "openOpen" => NumericIntervalsAttribute.Interval(ExclusiveIntervalBorder(from), ExclusiveIntervalBorder(to))
        case "closedOpen" => NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(from), ExclusiveIntervalBorder(to))
        case _ => NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(from), InclusiveIntervalBorder(to))
      }
    }
  }

  lazy val attributes: Seq[Attribute] = (pmml.node \ "DerivedField").map { derivedFieldNode =>
    DerivedField(derivedFieldNode \@ "name", derivedFieldNode)
  }.collect(simpleAttributeBuilderExtractor orElse nominalEnumerationAttributeBuilderExtractor orElse numericIntervalsAttributeBuilderExtractor)

}