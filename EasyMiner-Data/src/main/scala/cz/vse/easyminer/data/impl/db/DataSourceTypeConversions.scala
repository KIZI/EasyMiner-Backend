package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.data.DataSourceType.DataSourceTypeOps
import cz.vse.easyminer.data.impl.db.hive.HiveDataSourceTypeOps
import cz.vse.easyminer.data.impl.db.mysql.MysqlDataSourceTypeOps
import cz.vse.easyminer.data.{UnlimitedDataSourceType, LimitedDataSourceType}

/**
 * Created by propan on 15. 2. 2016.
 */
object DataSourceTypeConversions {

  import scala.language.implicitConversions

  implicit def limitedDataSourceTypeToMysqlDataSourceTypeOps(limitedDataSourceType: LimitedDataSourceType.type)
                                                      (implicit dBConnectors: DBConnectors,
                                                       taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : DataSourceTypeOps[LimitedDataSourceType.type] = new MysqlDataSourceTypeOps()

  implicit def unlimitedDataSourceTypeToHiveDataSourceTypeOps(unlimitedDataSourceType: UnlimitedDataSourceType.type)
                                                       (implicit dBConnectors: DBConnectors,
                                                        taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : DataSourceTypeOps[UnlimitedDataSourceType.type] = new HiveDataSourceTypeOps()

}
