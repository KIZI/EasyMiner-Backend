package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.DatasetTable
import scalikejdbc._

/**
 * Created by propan on 22. 12. 2015.
 */
trait DbDatasetBuilder extends DatasetBuilder {

  private[db] val mysqlDBConnector: MysqlDBConnector

  private[db] val datasetOps: DatasetOps

  private[db] def buildInstanceTable(datasetDetail: DatasetDetail): Unit

  private[db] val taskStatusProcessor: TaskStatusProcessor

  implicit class DataSourceTypeConversion(dataSourceType: DataSourceType) {

    def toSqlString = dataSourceType match {
      case LimitedDataSourceType => "LIMITED"
      case UnlimitedDataSourceType => "UNLIMITED"
    }

  }

  import mysqlDBConnector._

  final def build: DatasetDetail = {
    val datasetId = DBConn autoCommit { implicit session =>
      sql"""INSERT INTO ${DatasetTable.table}
            (${DatasetTable.column.name}, ${DatasetTable.column.dataSource}, ${DatasetTable.column.`type`}, ${DatasetTable.column.size})
            VALUES
            (${dataset.name}, ${dataset.dataSourceDetail.id}, ${dataset.dataSourceDetail.`type`.toSqlString}, ${dataset.dataSourceDetail.size})
        """.updateAndReturnGeneratedKey().apply().toInt
    }
    taskStatusProcessor.newStatus("Dataset meta information have been created. The instance table building is now in progress...")
    val datasetDetail = DatasetDetail(datasetId, dataset.name, dataset.dataSourceDetail.id, DatasetType(dataset.dataSourceDetail.`type`), dataset.dataSourceDetail.size, false)
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