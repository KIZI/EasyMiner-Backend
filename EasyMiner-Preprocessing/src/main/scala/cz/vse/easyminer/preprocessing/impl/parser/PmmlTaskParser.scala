/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.parser

import cz.vse.easyminer.core.util.XmlUtils.TrimmedXml
import cz.vse.easyminer.core.util.{AnyToDouble, AnyToInt}
import cz.vse.easyminer.data.{ExclusiveIntervalBorder, InclusiveIntervalBorder, IntervalBorder}
import cz.vse.easyminer.preprocessing._

import scala.collection.mutable.ListBuffer
import scala.util.Try

/**
  * Created by Vaclav Zeman on 2. 2. 2016.
  */

/**
  * This class loads an input PMML document, parses it and creates attribute definitions for transformation fields to attributes with some preprocessing
  *
  * @param pmml PMML xml document
  */
class PmmlTaskParser(pmml: TrimmedXml) extends TaskParser {

  case class DerivedField(name: String, node: xml.Node)

  private implicit class PimpedExtensions(extensions: Seq[(String, String)]) {
    def leftMargin: Option[IntervalBorder] = extensions.collectFirst {
      case ("leftMarginOpen", AnyToDouble(value)) => ExclusiveIntervalBorder(value)
      case ("leftMarginClose", AnyToDouble(value)) => InclusiveIntervalBorder(value)
    }

    def rightMargin: Option[IntervalBorder] = extensions.collectFirst {
      case ("rightMarginOpen", AnyToDouble(value)) => ExclusiveIntervalBorder(value)
      case ("rightMarginClose", AnyToDouble(value)) => InclusiveIntervalBorder(value)
    }

    def hasPreserveUncovered = extensions.collectFirst {
      case ("preserveUncovered", value) => Try(value.toBoolean).getOrElse(false)
    }.getOrElse(false)
  }

  private def getAttributeFeatures(derivedField: DerivedField) = {
    val extensions = (derivedField.node \\ "Extension").map(x => (x \@ "name") -> (x \@ "value"))
    val listBuffer = ListBuffer.empty[AttributeFeature]
    val (lm, rm) = (extensions.leftMargin, extensions.rightMargin)
    if (lm.nonEmpty || rm.nonEmpty) listBuffer += IntervalsBorder(lm, rm)
    if (extensions.hasPreserveUncovered) listBuffer += PreserveUncovered
    listBuffer.toList
  }

  /**
    * Partial function for creation of simple attribute definition from DerivedField
    */
  private val simpleAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = (x.node \ "MapValues" \ "FieldColumnPair").headOption.exists(_.child.isEmpty)

    def apply(v1: DerivedField): Attribute = {
      val fieldId = AnyToInt.unapply(v1.node \ "MapValues" \ "FieldColumnPair" \@ "field").getOrElse(0)
      SimpleAttribute(v1.name, fieldId, getAttributeFeatures(v1))
    }
  }

  /**
    * Partial function for creation of nominal enumeration attribute definition from DerivedField
    */
  private val nominalEnumerationAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = (x.node \ "MapValues" \ "FieldColumnPair").headOption.exists(_.child.isEmpty) && (x.node \ "MapValues" \ "InlineTable" \ "row").nonEmpty

    def apply(v1: DerivedField): Attribute = {
      val fieldId = AnyToInt.unapply(v1.node \ "MapValues" \ "FieldColumnPair" \@ "field").getOrElse(0)
      val mapping = (v1.node \ "MapValues" \ "InlineTable" \ "row").collect {
        case <row><column>{xml.Text(originalValue)}</column><field>{xml.Text(targetValue)}</field></row> => targetValue -> originalValue
      }.groupBy(_._1).iterator.map(x => NominalEnumerationAttribute.Bin(x._1, x._2.map(_._2))).toList
      NominalEnumerationAttribute(v1.name, fieldId, mapping, getAttributeFeatures(v1))
    }
  }

  /**
    * Partial function for creation of equidistant intervals attribute definition from DerivedField
    */
  private val equidistantIntervalsAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = {
      val discretize = x.node \ "Discretize"
      val extensions = (discretize \ "Extension").map(x => (x \@ "name") -> (x \@ "value")).toMap
      AnyToInt.unapply(discretize \@ "field").nonEmpty && extensions.get("algorithm").contains("equidistant-intervals") && extensions.get("bins").exists(AnyToInt.unapply(_).nonEmpty)
    }

    def apply(v1: DerivedField): Attribute = {
      val discretize = v1.node \ "Discretize"
      val extensions = discretize \ "Extension"
      val bins = extensions.find(x => (x \@ "name") == "bins").flatMap(x => AnyToInt.unapply(x \@ "value")).get
      EquidistantIntervalsAttribute(v1.name, AnyToInt.unapply(discretize \@ "field").get, bins, getAttributeFeatures(v1))
    }
  }

  /**
    * Partial function for creation of equifrequent intervals attribute definition from DerivedField
    */
  private val equifrequentIntervalsAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = {
      val discretize = x.node \ "Discretize"
      val extensions = (discretize \ "Extension").map(x => (x \@ "name") -> (x \@ "value")).toMap
      AnyToInt.unapply(discretize \@ "field").nonEmpty && extensions.get("algorithm").contains("equifrequent-intervals") && extensions.get("bins").exists(AnyToInt.unapply(_).nonEmpty)
    }

    def apply(v1: DerivedField): Attribute = {
      val discretize = v1.node \ "Discretize"
      val extensions = discretize \ "Extension"
      val bins = extensions.find(x => (x \@ "name") == "bins").flatMap(x => AnyToInt.unapply(x \@ "value")).get
      EquifrequentIntervalsAttribute(v1.name, AnyToInt.unapply(discretize \@ "field").get, bins, getAttributeFeatures(v1))
    }
  }

  /**
    * Partial function for creation of equisized intervals attribute definition from DerivedField
    */
  private val equisizedIntervalsAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = {
      val discretize = x.node \ "Discretize"
      val extensions = (discretize \ "Extension").map(x => (x \@ "name") -> (x \@ "value")).toMap
      AnyToInt.unapply(discretize \@ "field").nonEmpty && extensions.get("algorithm").contains("equisized-intervals") && extensions.get("support").exists(AnyToDouble.unapply(_).nonEmpty)
    }

    def apply(v1: DerivedField): Attribute = {
      val discretize = v1.node \ "Discretize"
      val extensions = discretize \ "Extension"
      val support = extensions.find(x => (x \@ "name") == "support").flatMap(x => AnyToDouble.unapply(x \@ "value")).get
      EquisizedIntervalsAttribute(v1.name, AnyToInt.unapply(discretize \@ "field").get, support, getAttributeFeatures(v1))
    }
  }

  /**
    * Partial function for creation of user-specified intervals attribute definition from DerivedField
    */
  private val numericIntervalsAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = (x.node \ "Discretize" \ "DiscretizeBin").nonEmpty

    def apply(v1: DerivedField): Attribute = {
      val fieldId = AnyToInt.unapply(v1.node \ "Discretize" \@ "field").getOrElse(0)
      val intervals = (v1.node \ "Discretize" \ "DiscretizeBin").flatMap { discretizeBinNode =>
        val binValue = discretizeBinNode \@ "binValue"
        (discretizeBinNode \ "Interval").map(intervalNodeToInterval).map(binValue -> _)
      }.groupBy(_._1).iterator.map(x => NumericIntervalsAttribute.Bin(x._1, x._2.map(_._2))).toList.sortBy(_.intervals.minBy(_.from.value).from.value)
      NumericIntervalsAttribute(v1.name, fieldId, intervals, getAttributeFeatures(v1))
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

  /**
    * Create attribute definitions from PMML
    * It tries to parse definitions one by one and it returns empty collection if PMML is not parsable
    */
  lazy val attributes: Seq[Attribute] = (pmml.node \ "DerivedField").map { derivedFieldNode =>
    DerivedField(derivedFieldNode \@ "name", derivedFieldNode)
  }.collect(nominalEnumerationAttributeBuilderExtractor
    orElse numericIntervalsAttributeBuilderExtractor
    orElse equidistantIntervalsAttributeBuilderExtractor
    orElse equifrequentIntervalsAttributeBuilderExtractor
    orElse equisizedIntervalsAttributeBuilderExtractor
    orElse simpleAttributeBuilderExtractor
  )

}