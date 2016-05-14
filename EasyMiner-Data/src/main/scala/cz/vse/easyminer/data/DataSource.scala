package cz.vse.easyminer.data

import cz.vse.easyminer.core.db._

/**
 * Created by propan on 26. 7. 2015.
 */
case class DataSource(name: String, `type`: DataSourceType)

case class DataSourceDetail(id: Int, name: String, `type`: DataSourceType, size: Int, active: Boolean)

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

    protected[this] val dBConnectors: DBConnectors

    implicit protected[this] def dataSourceTypeToConnector[A <: DBConnector[_]](dataSourceType: DataSourceType): A = dataSourceType match {
      case LimitedDataSourceType => dBConnectors.connector(LimitedDBType)
      case UnlimitedDataSourceType => dBConnectors.connector(UnlimitedDBType)
    }

    def toDataSourceBuilder(name: String): DataSourceBuilder

    def toDataSourceOps: DataSourceOps

    def toFieldOps(dataSourceDetail: DataSourceDetail): FieldOps

    def toValueOps(dataSourceDetail: DataSourceDetail, fieldDetail: FieldDetail): ValueOps

  }

}