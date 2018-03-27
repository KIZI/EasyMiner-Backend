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

/**
  * Simple abstraction for attribute which is created from a field as the preprocessed field
  */
sealed trait Attribute {
  val name: String
  val field: Int
  val features: Seq[AttributeFeature]
}

sealed trait AttributeFeature

case class IntervalsBorder(min: Option[IntervalBorder], max: Option[IntervalBorder]) extends AttributeFeature

object PreserveUncovered extends AttributeFeature

/**
  * Simple attribute is created as the clone of a field
  *
  * @param name  attribute name
  * @param field field id
  */
case class SimpleAttribute(name: String, field: Int, features: Seq[AttributeFeature]) extends Attribute

/**
  * User specified mappings of field values to attribute bins.
  * This represents attribute which is created from this mappings rules.
  *
  * @param name  attribute name
  * @param field field id
  * @param bins  mapping rules
  */
case class NominalEnumerationAttribute(name: String, field: Int, bins: Seq[NominalEnumerationAttribute.Bin], features: Seq[AttributeFeature]) extends Attribute

object NominalEnumerationAttribute {

  case class Bin(name: String, values: Seq[String])

}

/**
  * User specified mappings of field numeric values to manually created intervals.
  * This represents attribute which is created from this mappings rules.
  *
  * @param name  attribute name
  * @param field field id
  * @param bins  mapping rules
  */
case class NumericIntervalsAttribute(name: String, field: Int, bins: Seq[NumericIntervalsAttribute.Bin], features: Seq[AttributeFeature]) extends Attribute

object NumericIntervalsAttribute {

  case class Bin(name: String, intervals: Seq[Interval])

  case class Interval(from: IntervalBorder, to: IntervalBorder)

}

/**
  * Attribute created from a numeric field where all field values are mapped into intervals.
  * Intervals are created automatically by equidistant algorithm.
  * Algorithm creates intervals which have the same range
  *
  * @param name  attribute name
  * @param field field id
  * @param bins  number of bins/intervals
  */
case class EquidistantIntervalsAttribute(name: String, field: Int, bins: Int, features: Seq[AttributeFeature]) extends Attribute

/**
  * Attribute created from a numeric field where all field values are mapped into intervals.
  * Intervals are created automatically by equifrequent algorithm.
  * Algorithm creates intervals which have a very similar frequency
  *
  * @param name  attribute name
  * @param field field id
  * @param bins  number of bins/intervals
  */
case class EquifrequentIntervalsAttribute(name: String, field: Int, bins: Int, features: Seq[AttributeFeature]) extends Attribute

/**
  * Attribute created from a numeric field where all field values are mapped into intervals.
  * Intervals are created automatically by equisize algorithm.
  * Algorithm creates intervals which have a minimal frequency
  *
  * @param name    attribute name
  * @param field   field id
  * @param support minimal relative frequency/support of each interval
  */
case class EquisizedIntervalsAttribute(name: String, field: Int, support: Double, features: Seq[AttributeFeature]) extends Attribute

/**
  * Object of created attribute which is saved in database
  *
  * @param id               attribute id
  * @param name             attribute name
  * @param field            field id
  * @param dataset          dataset id
  * @param uniqueValuesSize number of distinct values for this attribute
  * @param active           flag that indicates whether this attribute is active or not (if any attribute is inactive then it is probably under construction)
  */
case class AttributeDetail(id: Int, name: String, field: Int, dataset: Int, uniqueValuesSize: Int, active: Boolean)

object AttributeDetail {

  implicit class PimpedAttributeDetail(attributeDetail: AttributeDetail)(implicit datasetTypeToDatasetTypeOps: DatasetType => DatasetTypeOps[DatasetType], attributeDetailToDatasetDetail: AttributeDetail => DatasetDetail) {
    def toValueOps = {
      val datasetDetail: DatasetDetail = attributeDetail
      datasetDetail.`type`.toValueOps(datasetDetail, attributeDetail)
    }
  }

}