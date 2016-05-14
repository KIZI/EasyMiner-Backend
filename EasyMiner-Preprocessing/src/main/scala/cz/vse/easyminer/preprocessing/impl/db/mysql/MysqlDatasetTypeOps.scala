package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.{MysqlDBConnector, DBConnectors}
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.TypeableCases._

/**
  * Created by propan on 15. 2. 2016.
  */
class MysqlDatasetTypeOps private[db](implicit protected[this] val dBConnectors: DBConnectors, taskStatusProcessor: TaskStatusProcessor) extends DatasetTypeOps[LimitedDatasetType.type] {

  implicit private val mysqlDBConnector: MysqlDBConnector = LimitedDatasetType

  def toDatasetBuilder(dataset: Dataset): DatasetBuilder = MysqlDatasetBuilder(dataset)

  def toAttributeBuilder(datasetDetail: DatasetDetail, attribute: Attribute): AttributeBuilder = attribute match {
    case attribute: SimpleAttribute => MysqlSimpleAttributeBuilder(attribute, datasetDetail)
  }

  def toDatasetOps: DatasetOps = MysqlDatasetOps()

  def toAttributeOps(datasetDetail: DatasetDetail): AttributeOps = MysqlAttributeOps(datasetDetail)

  def toValueOps(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail): ValueOps = MysqlValueOps(datasetDetail, attributeDetail)

  def toValueMapperOps(dataset: DatasetDetail): ValueMapperOps = MysqlValueMapperOps(dataset)

  def toCollectiveAttributeBuilder[A <: Attribute](datasetDetail: DatasetDetail, attributes: A*): CollectiveAttributeBuilder[A] = attributes match {
    case `Seq[SimpleAttribute]`(attributes) => MysqlCollectiveSimpleAttributeBuilder(attributes, datasetDetail).asInstanceOf[CollectiveAttributeBuilder[A]]
    case _ => throw new IllegalArgumentException
  }

}