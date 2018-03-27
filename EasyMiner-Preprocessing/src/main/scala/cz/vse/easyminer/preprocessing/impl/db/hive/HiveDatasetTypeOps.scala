package cz.vse.easyminer.preprocessing.impl.db.hive

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.TypeableCases._
import cz.vse.easyminer.preprocessing.impl.db.MetaAttributeBuilder

/**
  * Created by propan on 15. 2. 2016.
  */
class HiveDatasetTypeOps private[db](implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector, taskStatusProcessor: TaskStatusProcessor) extends DatasetTypeOps[UnlimitedDatasetType.type] {

  def toDatasetBuilder(dataset: Dataset): DatasetBuilder = HiveDatasetBuilder(dataset)

  def toDatasetOps: DatasetOps = HiveDatasetOps()

  def toAttributeOps(datasetDetail: DatasetDetail): AttributeOps = HiveAttributeOps(datasetDetail)

  def toValueOps(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail): ValueOps = HiveValueOps(datasetDetail, attributeDetail)

  def toValueMapperOps(dataset: DatasetDetail): ValueMapperOps = HiveValueMapperOps(dataset)

  def toAttributeBuilder(datasetDetail: DatasetDetail, attributes: Attribute*): AttributeBuilder[Attribute] = new MetaAttributeBuilder(datasetDetail, attributes, HiveAttributeOps(datasetDetail))({
    case `Seq[SimpleAttribute]`(attributes) => HiveSimpleAttributeBuilder(attributes, datasetDetail)
    case _ => throw new IllegalArgumentException
  })

}