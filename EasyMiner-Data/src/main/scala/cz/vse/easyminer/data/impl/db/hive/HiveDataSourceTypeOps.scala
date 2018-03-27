package cz.vse.easyminer.data.impl.db.hive

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.data.DataSourceType.DataSourceTypeOps
import cz.vse.easyminer.data._

/**
 * Created by propan on 16. 2. 2016.
 */
class HiveDataSourceTypeOps private[db](implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector, taskStatusProcessor: TaskStatusProcessor) extends DataSourceTypeOps[UnlimitedDataSourceType.type] {

  def toDataSourceBuilder(name: String): DataSourceBuilder = HiveDataSourceBuilder(name)

  def toDataSourceOps: DataSourceOps = HiveDataSourceOps()

  def toFieldOps(dataSourceDetail: DataSourceDetail): FieldOps = HiveFieldOps(dataSourceDetail)

  def toValueOps(dataSourceDetail: DataSourceDetail, fieldDetail: FieldDetail): ValueOps = HiveValueOps(dataSourceDetail, fieldDetail)

}