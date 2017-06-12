/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db._
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlDatasetTypeOps
import cz.vse.easyminer.preprocessing.{DatasetType, LimitedDatasetType, UnlimitedDatasetType}

/**
  * Created by Vaclav Zeman on 15. 2. 2016.
  */

/**
  * Implicit conversions which convert dataset type to dataset type operations for various types of database connection.
  */
object DatasetTypeConversions {

  import scala.language.implicitConversions

  implicit def limitedDatasetTypeToMysqlDatasetTypeOps(limitedDatasetType: LimitedDatasetType.type)
                                                      (implicit dBConnectors: DBConnectors,
                                                       taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : DatasetTypeOps[LimitedDatasetType.type] = new MysqlDatasetTypeOps()(dBConnectors.connector(LimitedDBType), taskStatusProcessor)

  implicit def unlimitedDatasetTypeToHiveDatasetTypeOps(unlimitedDatasetType: UnlimitedDatasetType.type)
                                                       (implicit dBConnectors: DBConnectors,
                                                        taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : DatasetTypeOps[UnlimitedDatasetType.type] = ???

  object Limited {
    implicit def datasetTypeToMysqlDatasetTypeOps(limitedDatasetType: DatasetType)
                                                 (implicit mysqlDBConnector: MysqlDBConnector,
                                                  taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
    : DatasetTypeOps[LimitedDatasetType.type] = limitedDatasetType match {
      case LimitedDatasetType => new MysqlDatasetTypeOps()
      case UnlimitedDatasetType => throw new IllegalArgumentException
    }
  }

  object Unlimited {
    implicit def datasetTypeToHiveDatasetTypeOps(unlimitedDatasetType: DatasetType)
                                                (implicit mysqlDBConnector: MysqlDBConnector,
                                                 taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
    : DatasetTypeOps[UnlimitedDatasetType.type] = unlimitedDatasetType match {
      case LimitedDatasetType => throw new IllegalArgumentException
      case UnlimitedDatasetType => ???
    }
  }

}
