package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.{DBConnectors, MysqlDBConnector}
import cz.vse.easyminer.data.DataSourceType.DataSourceTypeOps
import cz.vse.easyminer.data._

/**
 * Created by propan on 16. 2. 2016.
 */
class MysqlDataSourceTypeOps private[db](implicit protected[this] val dBConnectors: DBConnectors, taskStatusProcessor: TaskStatusProcessor) extends DataSourceTypeOps[LimitedDataSourceType.type] {

  implicit private val mysqlDBConnector: MysqlDBConnector = LimitedDataSourceType

  def toDataSourceBuilder(name: String): DataSourceBuilder = MysqlDataSourceBuilder(name)

  def toDataSourceOps: DataSourceOps = MysqlDataSourceOps()

  def toFieldOps(dataSourceDetail: DataSourceDetail): FieldOps = MysqlFieldOps(dataSourceDetail)

  def toValueOps(dataSourceDetail: DataSourceDetail, fieldDetail: FieldDetail): ValueOps = MysqlValueOps(dataSourceDetail, fieldDetail)

}