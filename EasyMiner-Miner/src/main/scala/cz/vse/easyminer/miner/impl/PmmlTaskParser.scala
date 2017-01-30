package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.core.util.{AnyToDouble, AnyToInt}
import cz.vse.easyminer.data
import cz.vse.easyminer.data.NominalValue
import cz.vse.easyminer.miner.impl.PmmlTaskParser.Exceptions._
import cz.vse.easyminer.miner.{Attribute, _}
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing._

class PmmlTaskParser(pmml: xml.Node)(implicit datasetTypeConv: DatasetType => DatasetTypeOps[DatasetType]) extends {

  type ItemMapper = Map[Int, Attribute]

  private val itemsetElemName = "DBASetting"
  private val itemElemName = "BBASetting"
  private val itemElemRefName = "BASettingRef"
  private val itemTypeElemName = "Coefficient"
  private val itemTypeNameElemName = "Type"
  private val itemFixedValue = "One category"
  private val itemFixedValueElemName = "Category"
  private val itemName = "FieldRef"
  private val boolSignElemName = "LiteralSign"
  private val interestMeasureElemName = "InterestMeasure"
  private val interestThresholdElemName = "Threshold"

  private def getElementById(name: String, id: Int) = pmml \\ name find (x => (x \ "@id").text == id.toString)

  private def findElemByElemId(el: xml.Node, elt: String) = cz.vse.easyminer.core.util.Lift(el.text) {
    case AnyToInt(id) => getElementById(elt, id)
  }

  private def toExpression(el: xml.Node)(implicit itemMapper: ItemMapper): BoolExpression[Attribute] = el.label match {
    case `itemsetElemName` =>
      val rs = el \ itemElemRefName
      val elt = (el \ "@type").text
      def nextExps(elt: String) = rs.map(node => findElemByElemId(node, elt).map(toExpression).getOrElse(throw InvalidItemNodeId))
      if (rs.size > 1) {
        val joinExp = elt match {
          case "Disjunction" => (e1: BoolExpression[Attribute], e2: BoolExpression[Attribute]) => e1 OR e2
          case _ => (e1: BoolExpression[Attribute], e2: BoolExpression[Attribute]) => e1 AND e2
        }
        nextExps(itemsetElemName) reduceLeft joinExp
      } else if (rs.size == 1 && elt == "Literal") {
        (el \ boolSignElemName).text match {
          case "Negative" => nextExps(itemElemName).head.NOT
          case _ => nextExps(itemElemName).head
        }
      } else {
        nextExps(itemsetElemName).head
      }
    case `itemElemName` => Value(AnyToInt.unapply((el \ "@id").text).flatMap(itemMapper.get).getOrElse(throw InvalidItemNodeId))
    case elabel => throw new UnspecifiedElement(elabel)
  }

  private def fetchItemMapper(datasetDetail: DatasetDetail): ItemMapper = {
    val attributeOps = datasetDetail.`type`.toAttributeOps(datasetDetail)
    val valueMapperOps = datasetDetail.`type`.toValueMapperOps(datasetDetail)
    val items =
      for {
        item <- pmml \\ itemElemName
        itemType <- item \ itemTypeElemName
      } yield {
        val nodeId = AnyToInt.unapply((item \ "@id").text).getOrElse(throw InvalidItemNodeId)
        val attributeId = AnyToInt.unapply((item \ itemName).text).getOrElse(throw InvalidAttributeId)
        val value = if ((itemType \ itemTypeNameElemName).text == itemFixedValue) (itemType \ itemFixedValueElemName).text.trim else ""
        (nodeId, attributeId, value)
      }
    val attributeMap = {
      val attributeIds = items.map(_._2).toSet
      val attributeMap = attributeOps.getAllAttributes.toIterator.map(attributeDetail => attributeDetail.id -> attributeDetail).filter(x => attributeIds(x._1)).toMap
      attributeIds.diff(attributeMap.keySet).headOption.foreach(attributeId => throw new AttributeDoesNotExist(attributeId))
      attributeMap
    }
    val valueMapper = valueMapperOps.valueMapper(
      items.groupBy(_._2).map { case (attributeId, values) =>
        val attributeDetail = attributeMap(attributeId)
        attributeDetail -> values.toIterator.map(_._3).filter(_.nonEmpty).map(NominalValue).toSet
      }
    )
    items.toIterator.map { case (nodeId, attributeId, value) =>
      val attributeDetail = attributeMap(attributeId)
      val taskAttribute = if (value.nonEmpty) {
        FixedValue(attributeDetail, valueMapper.item(attributeDetail, NominalValue(value)).getOrElse(throw new ValueDoesNotExist(attributeId, NominalValue(value))))
      } else {
        AllValues(attributeDetail)
      }
      nodeId -> taskAttribute
    }.toMap
  }

  private def fetchDataset = {
    val datasetOps = LimitedDatasetType.toDatasetOps
    (pmml \ "Header" \ "Extension")
      .find(ext => (ext \ "@name").text == "dataset")
      .map(ext => (ext \ "@value").text)
      .collect {
        case AnyToInt(datasetId) => datasetOps.getDataset(datasetId)
      }
      .flatten.getOrElse(throw NoDataset)
  }

  private def fetchInterestMeasures = {
    val limit = (pmml \\ "HypothesesCountMax")
      .map(_.text)
      .collectFirst {
        case AnyToInt(x) if x > 0 => Limit(x)
      }.toSet
    val im: Set[InterestMeasure] = (pmml \\ "InterestMeasureThreshold")
      .map(x => (x \ interestMeasureElemName).text -> (x \ interestThresholdElemName).text)
      .collect {
        case ("FUI" | "CONF", AnyToDouble(v)) => Confidence(v)
        case ("SUPP" | "BASE", AnyToDouble(v)) => Support(v)
        case ("LIFT", AnyToDouble(v)) => Lift(v)
        case ("RULE_LENGTH", AnyToInt(v)) => MaxRuleLength(v)
        case ("CBA", _) => CBA
        case ("AUTO_CONF_SUPP", _) => Auto
      }.toSet
    InterestMeasures(im ++ limit + MinRuleLength(1))
  }

  private def fetchAntecedent(implicit itemMapper: ItemMapper): Option[BoolExpression[Attribute]] = (pmml \\ "AntecedentSetting").headOption.flatMap(x => findElemByElemId(x, itemsetElemName) map toExpression)

  private def fetchConsequent(implicit itemMapper: ItemMapper): Option[BoolExpression[Attribute]] = (pmml \\ "ConsequentSetting").headOption.flatMap(x => findElemByElemId(x, itemsetElemName) map toExpression)


  def parse: MinerTask = {
    val dataset = fetchDataset
    implicit val itemMapper = fetchItemMapper(dataset)
    MinerTask(
      dataset,
      fetchAntecedent,
      fetchInterestMeasures,
      fetchConsequent
    )
  }

}

object PmmlTaskParser {

  object Exceptions {

    object NoDataset extends ValidationException("No dataset extension in the input PMML.")

    object InvalidItemNodeId extends ValidationException("A node BBASetting or DBASetting has bad ID attribute (it must be an integer).")

    object InvalidAttributeId extends ValidationException("A node FieldRef must be an attribute ID.")

    class AttributeDoesNotExist(attributeId: Int) extends ValidationException(s"The attribute with ID '$attributeId' does not exist.")

    class ValueDoesNotExist(attributeId: Int, value: data.Value) extends ValidationException(s"The value '$value' does not exist within attribute $attributeId.")

    class UnspecifiedElement(label: String) extends ValidationException(s"Unspecified element label: $label")

  }

}