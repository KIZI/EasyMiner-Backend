package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.data.Value
import cz.vse.easyminer.preprocessing.ValueMapperOps.{ItemMapper, ValueMapper}

/**
  * Created by propan on 13. 2. 2016.
  */
trait ValueMapperOps {

  val dataset: DatasetDetail

  def valueMapper(values: Map[AttributeDetail, Set[Value]]): ValueMapper

  def itemMapper(items: Map[AttributeDetail, Set[Int]]): ItemMapper

}

object ValueMapperOps {

  trait ValueMapper {
    def item(attributeDetail: AttributeDetail, value: Value): Option[Int]
  }

  trait ItemMapper {
    def value(attributeDetail: AttributeDetail, item: Int): Option[Value]
  }

}