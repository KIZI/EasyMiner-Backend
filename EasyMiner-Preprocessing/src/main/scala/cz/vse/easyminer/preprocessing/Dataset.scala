package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.data.{DataSourceDetail, DataSourceType, LimitedDataSourceType, UnlimitedDataSourceType}
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps

import scala.language.implicitConversions

/**
  * Created by propan on 18. 12. 2015.
  */
case class Dataset(name: String, dataSourceDetail: DataSourceDetail)

case class DatasetDetail(id: Int, name: String, dataSource: Int, `type`: DatasetType, size: Int, active: Boolean)

object DatasetDetail {

  implicit def attributeDetailToDatasetDetail(attributeDetail: AttributeDetail)(implicit datasetDetail: DatasetDetail): DatasetDetail = if (datasetDetail.id == attributeDetail.dataset) {
    datasetDetail
  } else {
    throw new IllegalArgumentException
  }

  implicit def datasetDetailToDatasetTypeOps(datasetDetail: DatasetDetail)
                                            (implicit datasetTypeToDatasetTypeOps: DatasetType => DatasetTypeOps[DatasetType])
  : DatasetTypeOps[DatasetType] = datasetDetail.`type`

}

sealed trait DatasetType

object LimitedDatasetType extends DatasetType

object UnlimitedDatasetType extends DatasetType

object DatasetType {

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

    def toDatasetBuilder(dataset: Dataset): DatasetBuilder

    def toAttributeBuilder(datasetDetail: DatasetDetail, attributes: Attribute*): AttributeBuilder[Attribute]

    def toDatasetOps: DatasetOps

    def toAttributeOps(datasetDetail: DatasetDetail): AttributeOps

    def toValueOps(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail): ValueOps

    def toValueMapperOps(dataset: DatasetDetail): ValueMapperOps

  }

}