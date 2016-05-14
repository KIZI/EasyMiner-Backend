package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.impl.db.mysql.Tables.{InstanceTable => DataInstanceTable}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeTable, InstanceTable => PreprocessingInstanceTable, ValueTable}
import cz.vse.easyminer.preprocessing.impl.db.{DbDatasetBuilder, ValidationDatasetBuilder}
import scalikejdbc._

/**
 * Created by propan on 21. 12. 2015.
 */
class MysqlDatasetBuilder private[db](val dataset: Dataset,
                                      private[db] val datasetOps: DatasetOps)
                                     (implicit
                                      private[db] val mysqlDBConnector: MysqlDBConnector,
                                      private[db] val taskStatusProcessor: TaskStatusProcessor) extends DbDatasetBuilder {

  import mysqlDBConnector._

  private[db] def buildInstanceTable(datasetDetail: DatasetDetail): Unit = {
    val preprocessingInstanceTable = new PreprocessingInstanceTable(datasetDetail.id)
    val dataInstanceTable = new DataInstanceTable(dataset.dataSourceDetail.id)
    val valueTable = new ValueTable(datasetDetail.id)
    DBConn autoCommit { implicit session =>
      sql"CREATE TABLE ${preprocessingInstanceTable.table} (${preprocessingInstanceTable.column.id} int(10) unsigned NOT NULL AUTO_INCREMENT, PRIMARY KEY (${preprocessingInstanceTable.column.id})) ENGINE=InnoDB DEFAULT CHARSET=utf8".execute().apply()
      sql"INSERT ${preprocessingInstanceTable.table} SELECT ${dataInstanceTable.column.id} FROM ${dataInstanceTable.table}".execute().apply()
      taskStatusProcessor.newStatus("The instance table has been populated successfully. Now aggregated data are creating...")
      sql"""CREATE TABLE ${valueTable.table} (
        ${valueTable.column.id} int(10) unsigned NOT NULL,
        ${valueTable.column.attribute} int(10) unsigned NOT NULL,
        ${valueTable.column.valueNominal} varchar(255) DEFAULT NULL,
        ${valueTable.column.valueNumeric} double DEFAULT NULL,
        ${valueTable.column.frequency} int(10) unsigned NOT NULL,
        PRIMARY KEY (${valueTable.column.attribute}, ${valueTable.column.id}),
        UNIQUE KEY ${valueTable.column.attribute}_${valueTable.column.valueNominal} (${valueTable.column.attribute},${valueTable.column.valueNominal}),
        UNIQUE KEY ${valueTable.column.attribute}_${valueTable.column.valueNumeric} (${valueTable.column.attribute},${valueTable.column.valueNumeric}),
        KEY ${valueTable.column.attribute} (${valueTable.column.attribute})
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8""".execute().apply()
      sql"""ALTER TABLE ${valueTable.table}
        ADD CONSTRAINT ${valueTable.table}_ibfk_1
        FOREIGN KEY (${valueTable.column.attribute})
        REFERENCES ${AttributeTable.table} (${AttributeTable.column.id})
        ON DELETE CASCADE ON UPDATE CASCADE""".execute().apply()
    }
  }

}

object MysqlDatasetBuilder {

  def apply(dataset: Dataset)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): DatasetBuilder = new ValidationDatasetBuilder(
    new MysqlDatasetBuilder(dataset, MysqlDatasetOps())
  )

}