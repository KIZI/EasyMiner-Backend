package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.data.Value
import cz.vse.easyminer.preprocessing.ValueMapperOps.{NormalizedValueMapper, ValueMapper}

/**
 * Created by propan on 13. 2. 2016.
 */
trait ValueMapperOps {

  val dataset: DatasetDetail

  def valueMapper(values: Map[AttributeDetail, Set[Value]]): ValueMapper

  def normalizedValueMapper(normalizedValues: Map[AttributeDetail, Set[NormalizedValue]]): NormalizedValueMapper

}

object ValueMapperOps {

  trait ValueMapper {
    def normalizedValue(attributeDetail: AttributeDetail, value: Value): Option[NormalizedValue]
  }

  trait NormalizedValueMapper {
    def value(attributeDetail: AttributeDetail, normalizedValue: NormalizedValue): Option[Value]
  }

}