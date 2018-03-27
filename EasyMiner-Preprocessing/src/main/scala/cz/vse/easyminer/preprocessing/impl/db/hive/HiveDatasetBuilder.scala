package cz.vse.easyminer.preprocessing.impl.db.hive

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.hive.Tables.{InstanceTable => PreprocessingInstanceTable, ValueTable}
import cz.vse.easyminer.preprocessing.impl.db.{DbDatasetBuilder, ValidationDatasetBuilder}
import scalikejdbc._

/**
  * Created by propan on 21. 12. 2015.
  */
class HiveDatasetBuilder private(val dataset: Dataset,
                                 protected val datasetOps: DatasetOps)
                                (implicit
                                 protected val mysqlDBConnector: MysqlDBConnector,
                                 hiveDBConnector: HiveDBConnector,
                                 protected val taskStatusProcessor: TaskStatusProcessor) extends DbDatasetBuilder {

  import hiveDBConnector._

  protected def buildInstanceTable(datasetDetail: DatasetDetail): Unit = {
    val preprocessingInstanceTable = new PreprocessingInstanceTable(datasetDetail.id)
    val valueTable = new ValueTable(datasetDetail.id)
    DBConn autoCommit { implicit session =>
      sql"CREATE TABLE ${preprocessingInstanceTable.table} (${preprocessingInstanceTable.column.id} BIGINT, ${preprocessingInstanceTable.column.value} BIGINT) PARTITIONED BY (${preprocessingInstanceTable.column.attribute} INT) STORED AS ORC".execute().apply()
      sql"CREATE TABLE ${valueTable.table} (${valueTable.column.id} INT, ${valueTable.column.value} STRING, ${valueTable.column.frequency} INT) PARTITIONED BY (${valueTable.column.attribute} INT) STORED AS ORC".execute().apply()
      taskStatusProcessor.newStatus("All needed tables have been created.")
    }
  }

}

object HiveDatasetBuilder {

  def apply(dataset: Dataset)(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector, taskStatusProcessor: TaskStatusProcessor): DatasetBuilder = new ValidationDatasetBuilder(
    new HiveDatasetBuilder(dataset, HiveDatasetOps())
  )

}