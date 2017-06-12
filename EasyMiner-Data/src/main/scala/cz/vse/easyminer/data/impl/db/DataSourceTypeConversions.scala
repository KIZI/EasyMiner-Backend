/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db._
import cz.vse.easyminer.data.DataSourceType.DataSourceTypeOps
import cz.vse.easyminer.data.impl.db.mysql.{MysqlDataSourceTypeOps, MysqlRdfDataSource}
import cz.vse.easyminer.data._

/**
  * Created by Vaclav Zeman on 15. 2. 2016.
  */

/**
  * This object contains implicit conversions from data source type to data source operations object.
  * There are different operations for different data source types.
  * We need to resolve operations for limited data source type where we work with limited db connector;
  * and distinguish it from unlimited data source type where there are different connections and operations.
  */
object DataSourceTypeConversions {

  import scala.language.implicitConversions

  implicit def limitedDataSourceTypeToMysqlDataSourceTypeOps(limitedDataSourceType: LimitedDataSourceType.type)
                                                            (implicit dBConnectors: DBConnectors,
                                                             taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : DataSourceTypeOps[LimitedDataSourceType.type] = new MysqlDataSourceTypeOps()(dBConnectors.connector(LimitedDBType), taskStatusProcessor)

  implicit def directLimitedDataSourceTypeToMysqlDataSourceTypeOps(limitedDataSourceType: LimitedDataSourceType.type)
                                                                  (implicit mysqlDBConnector: MysqlDBConnector,
                                                                   taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : DataSourceTypeOps[LimitedDataSourceType.type] = new MysqlDataSourceTypeOps()

  implicit def unlimitedDataSourceTypeToHiveDataSourceTypeOps(unlimitedDataSourceType: UnlimitedDataSourceType.type)
                                                             (implicit dBConnectors: DBConnectors,
                                                              taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : DataSourceTypeOps[UnlimitedDataSourceType.type] = ???

  object Limited {
    implicit def dataSourceTypeToMysqlDataSourceTypeOps(limitedDataSourceType: DataSourceType)
                                                       (implicit mysqlDBConnector: MysqlDBConnector,
                                                        taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
    : DataSourceTypeOps[LimitedDataSourceType.type] = limitedDataSourceType match {
      case LimitedDataSourceType => new MysqlDataSourceTypeOps()
      case UnlimitedDataSourceType => throw new IllegalArgumentException
    }
  }

  object Unlimited {
    implicit def dataSourceTypeToHiveDataSourceTypeOps(unlimitedDataSourceType: DataSourceType)
                                                      (implicit mysqlDBConnector: MysqlDBConnector,
                                                       taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
    : DataSourceTypeOps[UnlimitedDataSourceType.type] = unlimitedDataSourceType match {
      case LimitedDataSourceType => throw new IllegalArgumentException
      case UnlimitedDataSourceType => ???
    }
  }

  implicit def dataSourceToRdfDataSource(dataSourceDetail: DataSourceDetail)
                                        (implicit dBConnectors: DBConnectors,
                                         taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : RdfDataSource = dataSourceDetail.`type` match {
    case LimitedDataSourceType => MysqlRdfDataSource(dataSourceDetail)(dBConnectors, taskStatusProcessor)
    case UnlimitedDataSourceType => ???
  }

}
