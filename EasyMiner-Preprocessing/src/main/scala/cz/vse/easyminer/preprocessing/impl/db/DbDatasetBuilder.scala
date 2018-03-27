/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import java.util.Date

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.DatasetTable
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 22. 12. 2015.
  */

/**
  * Abstraction for building dataset from data source
  * It only creates empty dataset prepared for adding attributes
  */
trait DbDatasetBuilder extends DatasetBuilder {

  /**
    * Mysql database connection
    */
  protected val mysqlDBConnector: MysqlDBConnector

  /**
    * Operations for all datasets - it is needed for rollback function (delete existed dataset)
    */
  protected val datasetOps: DatasetOps

  /**
    * Function for creating table where all preprocessed data will be placed
    *
    * @param datasetDetail dataset detail
    */
  protected def buildInstanceTable(datasetDetail: DatasetDetail): Unit

  /**
    * Task processor for monitoring dataset bulding
    */
  protected val taskStatusProcessor: TaskStatusProcessor

  implicit class DataSourceTypeConversion(dataSourceType: DataSourceType) {

    def toSqlString = dataSourceType match {
      case LimitedDataSourceType => "LIMITED"
      case UnlimitedDataSourceType => "UNLIMITED"
    }

  }

  import mysqlDBConnector._

  /**
    * This function creates all metadata about dataset and empty table for preprocessed data
    *
    * @return new dataset
    */
  final def build: DatasetDetail = {
    val datasetId = DBConn autoCommit { implicit session =>
      sql"""INSERT INTO ${DatasetTable.table}
            (${DatasetTable.column.name}, ${DatasetTable.column.dataSource}, ${DatasetTable.column.`type`}, ${DatasetTable.column.size})
            VALUES
            (${dataset.name}, ${dataset.dataSourceDetail.id}, ${dataset.dataSourceDetail.`type`.toSqlString}, ${dataset.dataSourceDetail.size})
        """.updateAndReturnGeneratedKey().apply().toInt
    }
    taskStatusProcessor.newStatus("Dataset meta information have been created. The instance table building is now in progress...")
    val datasetDetail = DatasetDetail(datasetId, dataset.name, dataset.dataSourceDetail.id, DatasetType(dataset.dataSourceDetail.`type`), dataset.dataSourceDetail.size, new Date(), new Date(), false)
    try {
      buildInstanceTable(datasetDetail)
      DBConn autoCommit { implicit session =>
        sql"UPDATE ${DatasetTable.table} SET ${DatasetTable.column.active} = 1 WHERE ${DatasetTable.column.id} = $datasetId".execute().apply()
      }
    } catch {
      case th: Throwable =>
        datasetOps.deleteDataset(datasetId)
        throw th
    }
    datasetDetail.copy(active = true)
  }

}