/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.data.IntervalBorder
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps

/**
  * Created by Vaclav Zeman on 18. 12. 2015.
  */
sealed trait Attribute {
  val name: String
  val field: Int
}

case class SimpleAttribute(name: String, field: Int) extends Attribute

case class NominalEnumerationAttribute(name: String, field: Int, bins: Seq[NominalEnumerationAttribute.Bin]) extends Attribute

object NominalEnumerationAttribute {

  case class Bin(name: String, values: Seq[String])

}

case class NumericIntervalsAttribute(name: String, field: Int, bins: Seq[NumericIntervalsAttribute.Bin]) extends Attribute

object NumericIntervalsAttribute {

  case class Bin(name: String, intervals: Seq[Interval])

  case class Interval(from: IntervalBorder, to: IntervalBorder)

}

case class EquidistantIntervalsAttribute(name: String, field: Int, bins: Int) extends Attribute

case class EquifrequentIntervalsAttribute(name: String, field: Int, bins: Int) extends Attribute

case class EquisizedIntervalsAttribute(name: String, field: Int, support: Double) extends Attribute

case class AttributeDetail(id: Int, name: String, field: Int, dataset: Int, uniqueValuesSize: Int, active: Boolean)

object AttributeDetail {

  implicit class PimpedAttributeDetail(attributeDetail: AttributeDetail)(implicit datasetTypeToDatasetTypeOps: DatasetType => DatasetTypeOps[DatasetType], attributeDetailToDatasetDetail: AttributeDetail => DatasetDetail) {
    def toValueOps = {
      val datasetDetail: DatasetDetail = attributeDetail
      datasetDetail.`type`.toValueOps(datasetDetail, attributeDetail)
    }
  }

}