/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

import cz.vse.easyminer.data.DataSourceType.DataSourceTypeOps
import scala.language.implicitConversions

/**
  * Created by Vaclav Zeman on 26. 7. 2015.
  */

/**
  * Simple object for creation a data source. It contains only name and type
  *
  * @param name   name of data source
  * @param `type` type (limited, unlimited)
  */
case class DataSource(name: String, `type`: DataSourceType)

/**
  * Detail of a data source which is saved in a database
  *
  * @param id     data source id
  * @param name   data source name
  * @param `type` data source type (limited, unlimited)
  * @param size   number of rows in the table (number of transactions)
  * @param active flag, that indicates whether data source is activated and ready for using
  */
case class DataSourceDetail(id: Int, name: String, `type`: DataSourceType, size: Int, active: Boolean)

object DataSourceDetail {

  /**
    * This converts data source detail to data source operation object, where there are methods, which we can invoke for the data source
    *
    * @param dataSourceDetail                  data source detail
    * @param dataSourceTypeToDataSourceTypeOps implicit! conversion function: data source type -> data source operations
    * @return data source operations object
    */
  implicit def dataSourceDetailToDataSourceTypeOps(dataSourceDetail: DataSourceDetail)
                                                  (implicit dataSourceTypeToDataSourceTypeOps: DataSourceType => DataSourceTypeOps[DataSourceType])
  : DataSourceTypeOps[DataSourceType] = dataSourceDetail.`type`

}

/**
  * Type of data source (limited or unlimited)
  */
sealed trait DataSourceType

object LimitedDataSourceType extends DataSourceType

object UnlimitedDataSourceType extends DataSourceType

object DataSourceType {

  /**
    * This converts data source type to data source operation object, where there are methods, which we can invoke for a specific data source type
    *
    * @param dataSourceType data source type
    * @param limitedConv    implicit! conversion function: limited data source type -> data source operations
    * @param unlimitedConv  implicit! conversion function: unlimited data source type -> data source operations
    * @return data source operations object
    */
  implicit def dataSourceTypeToDataSourceTypeOps(dataSourceType: DataSourceType)
                                                (implicit limitedConv: LimitedDataSourceType.type => DataSourceTypeOps[LimitedDataSourceType.type],
                                                 unlimitedConv: UnlimitedDataSourceType.type => DataSourceTypeOps[UnlimitedDataSourceType.type])
  : DataSourceTypeOps[DataSourceType] = dataSourceType match {
    case LimitedDataSourceType => LimitedDataSourceType
    case UnlimitedDataSourceType => UnlimitedDataSourceType
  }

  /**
    * This is abstraction for data source operations.
    * It contains methods, from which we can access to other data source operations.
    *
    * @tparam T data source type
    */
  trait DataSourceTypeOps[+T <: DataSourceType] {

    /**
      * This method creates builder for creation of a new data source and populating it with data
      *
      * @param name new data source name
      * @return data source builder
      */
    def toDataSourceBuilder(name: String): DataSourceBuilder

    /**
      * Get operations for data sources
      *
      * @return data sources operations object
      */
    def toDataSourceOps: DataSourceOps

    /**
      * Get field operations for a data source
      *
      * @param dataSourceDetail data source
      * @return field operations object
      */
    def toFieldOps(dataSourceDetail: DataSourceDetail): FieldOps

    /**
      * Get value operations for a field of a data source
      *
      * @param dataSourceDetail data source
      * @param fieldDetail      field
      * @return value operations object
      */
    def toValueOps(dataSourceDetail: DataSourceDetail, fieldDetail: FieldDetail): ValueOps

  }

}