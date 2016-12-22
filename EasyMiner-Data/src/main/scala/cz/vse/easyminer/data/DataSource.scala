package cz.vse.easyminer.data

import cz.vse.easyminer.data.DataSourceType.DataSourceTypeOps

/**
  * Created by propan on 26. 7. 2015.
  */
case class DataSource(name: String, `type`: DataSourceType)

case class DataSourceDetail(id: Int, name: String, `type`: DataSourceType, size: Int, active: Boolean)

object DataSourceDetail {

  implicit class PimpedDataSourceDetail(dataSourceDetail: DataSourceDetail)(implicit dataSourceTypeToDataSourceTypeOps: DataSourceType => DataSourceTypeOps[DataSourceType]) {
    def toFieldOps = dataSourceDetail.`type`.toFieldOps(dataSourceDetail)
  }

}

sealed trait DataSourceType

object LimitedDataSourceType extends DataSourceType

object UnlimitedDataSourceType extends DataSourceType

object DataSourceType {

  import scala.language.implicitConversions

  implicit def dataSourceTypeToDataSourceTypeOps(dataSourceType: DataSourceType)
                                                (implicit limitedConv: LimitedDataSourceType.type => DataSourceTypeOps[LimitedDataSourceType.type],
                                                 unlimitedConv: UnlimitedDataSourceType.type => DataSourceTypeOps[UnlimitedDataSourceType.type])
  : DataSourceTypeOps[DataSourceType] = dataSourceType match {
    case LimitedDataSourceType => LimitedDataSourceType
    case UnlimitedDataSourceType => UnlimitedDataSourceType
  }

  trait DataSourceTypeOps[+T <: DataSourceType] {

    def toDataSourceBuilder(name: String): DataSourceBuilder

    def toDataSourceOps: DataSourceOps

    def toFieldOps(dataSourceDetail: DataSourceDetail): FieldOps

    def toValueOps(dataSourceDetail: DataSourceDetail, fieldDetail: FieldDetail): ValueOps

  }

}