package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.core.db._
import cz.vse.easyminer.data.{DataSourceDetail, DataSourceType, LimitedDataSourceType, UnlimitedDataSourceType}

import scala.language.higherKinds

/**
 * Created by propan on 18. 12. 2015.
 */
case class Dataset(name: String, dataSourceDetail: DataSourceDetail)

case class DatasetDetail(id: Int, name: String, dataSource: Int, `type`: DatasetType, size: Int, active: Boolean)

sealed trait DatasetType

object LimitedDatasetType extends DatasetType

object UnlimitedDatasetType extends DatasetType

object DatasetType {

  import scala.language.implicitConversions

  implicit def datasetTypeToDatasetTypeOps(datasetType: DatasetType)
                                          (implicit limitedConv: LimitedDatasetType.type => DatasetTypeOps[LimitedDatasetType.type],
                                           unlimitedConv: UnlimitedDatasetType.type => DatasetTypeOps[UnlimitedDatasetType.type]): DatasetTypeOps[DatasetType] = datasetType match {
    case LimitedDatasetType => LimitedDatasetType
    case UnlimitedDatasetType => UnlimitedDatasetType
  }

  def apply(dataSourceType: DataSourceType) = dataSourceType match {
    case LimitedDataSourceType => LimitedDatasetType
    case UnlimitedDataSourceType => UnlimitedDatasetType
  }

  trait DatasetTypeOps[+T <: DatasetType] {

    protected[this] val dBConnectors: DBConnectors

    implicit protected[this] def datasetTypeToConnector[A <: DBConnector[_]](datasetType: DatasetType): A = datasetType match {
      case LimitedDatasetType => dBConnectors.connector(LimitedDBType)
      case UnlimitedDatasetType => dBConnectors.connector(UnlimitedDBType)
    }

    def toDatasetBuilder(dataset: Dataset): DatasetBuilder

    def toAttributeBuilder(datasetDetail: DatasetDetail, attribute: Attribute): AttributeBuilder

    def toCollectiveAttributeBuilder[A <: Attribute](datasetDetail: DatasetDetail, attributes: A*): CollectiveAttributeBuilder[A]

    def toDatasetOps: DatasetOps

    def toAttributeOps(datasetDetail: DatasetDetail): AttributeOps

    def toValueOps(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail): ValueOps

    def toValueMapperOps(dataset: DatasetDetail): ValueMapperOps

  }

}