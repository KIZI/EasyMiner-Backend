/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{ValueTable, InstanceTable => PreprocessingInstanceTable}
import cz.vse.easyminer.preprocessing.impl.db.{DbDatasetBuilder, ValidationDatasetBuilder}
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 21. 12. 2015.
  */

/**
  * Class for dataset building
  *
  * @param dataset             dataset for creation
  * @param datasetOps          dataset operations object
  * @param mysqlDBConnector    mysql database connection
  * @param taskStatusProcessor task processor for monitoring
  */
class MysqlDatasetBuilder private[db](val dataset: Dataset,
                                      private[db] val datasetOps: DatasetOps)
                                     (implicit
                                      private[db] val mysqlDBConnector: MysqlDBConnector,
                                      private[db] val taskStatusProcessor: TaskStatusProcessor) extends DbDatasetBuilder {

  import mysqlDBConnector._

  /**
    * Function for creating table where all preprocessed data will be placed
    *
    * @param datasetDetail dataset detail
    */
  private[db] def buildInstanceTable(datasetDetail: DatasetDetail): Unit = {
    val preprocessingInstanceTable = new PreprocessingInstanceTable(datasetDetail.id)
    val valueTable = new ValueTable(datasetDetail.id)
    DBConn autoCommit { implicit session =>
      //create instance table
      sql"""CREATE TABLE ${preprocessingInstanceTable.table} (
        pid int(10) unsigned NOT NULL AUTO_INCREMENT,
        ${preprocessingInstanceTable.column.id} int(10) unsigned NOT NULL,
        ${preprocessingInstanceTable.column.attribute} int(10) unsigned NOT NULL,
        ${preprocessingInstanceTable.column.value} int(10) unsigned NOT NULL,
        KEY ${preprocessingInstanceTable.column.attribute} (${preprocessingInstanceTable.column.attribute}),
        PRIMARY KEY (pid)
        ) ENGINE=MYISAM DEFAULT CHARSET=utf8 COLLATE=utf8_bin""".execute().apply()
      //create value table
      taskStatusProcessor.newStatus("The instance table has been populated successfully. Now aggregated data are creating...")
      sql"""CREATE TABLE ${valueTable.table} (
        ${valueTable.column.id} int(10) unsigned NOT NULL,
        ${valueTable.column.attribute} int(10) unsigned NOT NULL,
        ${valueTable.column.value} varchar(255) NOT NULL,
        ${valueTable.column.frequency} int(10) unsigned NOT NULL,
        PRIMARY KEY (${valueTable.column.attribute}, ${valueTable.column.id}),
        UNIQUE KEY ${valueTable.column.attribute}_${valueTable.column.value} (${valueTable.column.attribute},${valueTable.column.value}),
        KEY ${valueTable.column.attribute} (${valueTable.column.attribute})
        ) ENGINE=MYISAM DEFAULT CHARSET=utf8 COLLATE=utf8_bin""".execute().apply()
    }
  }

}

object MysqlDatasetBuilder {

  def apply(dataset: Dataset)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): DatasetBuilder = new ValidationDatasetBuilder(
    new MysqlDatasetBuilder(dataset, MysqlDatasetOps())
  )

}