/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.DataSourceType.DataSourceTypeOps
import cz.vse.easyminer.data._

/**
 * Created by Vaclav Zeman on 16. 2. 2016.
 */
class MysqlDataSourceTypeOps private[db](implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) extends DataSourceTypeOps[LimitedDataSourceType.type] {

  def toDataSourceBuilder(name: String): DataSourceBuilder = MysqlDataSourceBuilder(name)

  def toDataSourceOps: DataSourceOps = MysqlDataSourceOps()

  def toFieldOps(dataSourceDetail: DataSourceDetail): FieldOps = MysqlFieldOps(dataSourceDetail)

  def toValueOps(dataSourceDetail: DataSourceDetail, fieldDetail: FieldDetail): ValueOps = MysqlValueOps(dataSourceDetail, fieldDetail)

}