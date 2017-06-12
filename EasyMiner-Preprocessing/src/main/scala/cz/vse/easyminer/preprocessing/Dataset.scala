/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.data.{DataSourceDetail, DataSourceType, LimitedDataSourceType, UnlimitedDataSourceType}
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps

import scala.language.implicitConversions

/**
  * Created by Vaclav Zeman on 18. 12. 2015.
  */

/**
  * Dataset which is created from a data source as a preprocessed data of transactions
  *
  * @param name             dataset name
  * @param dataSourceDetail existed data source
  */
case class Dataset(name: String, dataSourceDetail: DataSourceDetail)

/**
  * Object of created dataset which is saved in database
  *
  * @param id         dataset id
  * @param name       dataset name
  * @param dataSource data source from which was this dataset created
  * @param `type`     dataset type (limited or unlimited)
  * @param size       number of transactions for this dataset
  * @param active     flag which indicates whether this dataset is active (if it is inactive it is probably under construction)
  */
case class DatasetDetail(id: Int, name: String, dataSource: Int, `type`: DatasetType, size: Int, active: Boolean)

object DatasetDetail {

  implicit def attributeDetailToDatasetDetail(attributeDetail: AttributeDetail)(implicit datasetDetail: DatasetDetail): DatasetDetail = if (datasetDetail.id == attributeDetail.dataset) {
    datasetDetail
  } else {
    throw new IllegalArgumentException
  }

  /**
    * Implicit conversion of dataset detail into dataset type operations
    *
    * @param datasetDetail               dataset detail
    * @param datasetTypeToDatasetTypeOps implicit! conversion function from dataset type to dataset type operations
    * @return dataset type operations
    */
  implicit def datasetDetailToDatasetTypeOps(datasetDetail: DatasetDetail)
                                            (implicit datasetTypeToDatasetTypeOps: DatasetType => DatasetTypeOps[DatasetType])
  : DatasetTypeOps[DatasetType] = datasetDetail.`type`

}

sealed trait DatasetType

object LimitedDatasetType extends DatasetType

object UnlimitedDatasetType extends DatasetType

object DatasetType {

  /**
    * Implicit function which converts dataset type to dataset type operations
    *
    * @param datasetType   dataset type
    * @param limitedConv   implicit! conversion of limited dataset type to dataset type operations
    * @param unlimitedConv implicit! conversion of unlimited dataset type to dataset type operations
    * @return dataset type operations
    */
  implicit def datasetTypeToDatasetTypeOps(datasetType: DatasetType)
                                          (implicit limitedConv: LimitedDatasetType.type => DatasetTypeOps[LimitedDatasetType.type],
                                           unlimitedConv: UnlimitedDatasetType.type => DatasetTypeOps[UnlimitedDatasetType.type]): DatasetTypeOps[DatasetType] = datasetType match {
    case LimitedDatasetType => LimitedDatasetType
    case UnlimitedDatasetType => UnlimitedDatasetType
  }

  /**
    * Create dataset type from data source type
    *
    * @param dataSourceType data source type
    * @return dataset type
    */
  def apply(dataSourceType: DataSourceType) = dataSourceType match {
    case LimitedDataSourceType => LimitedDatasetType
    case UnlimitedDataSourceType => UnlimitedDatasetType
  }

  /**
    * Abstraction for dataset type operations.
    * Operations are for creation of dataset/attribute/value operations objects
    *
    * @tparam T dataset type
    */
  trait DatasetTypeOps[+T <: DatasetType] {

    def toDatasetBuilder(dataset: Dataset): DatasetBuilder

    def toAttributeBuilder(datasetDetail: DatasetDetail, attributes: Attribute*): AttributeBuilder[Attribute]

    def toDatasetOps: DatasetOps

    def toAttributeOps(datasetDetail: DatasetDetail): AttributeOps

    def toValueOps(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail): ValueOps

    def toValueMapperOps(dataset: DatasetDetail): ValueMapperOps

  }

}