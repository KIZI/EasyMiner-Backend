package cz.vse.easyminer.miner

import cz.vse.easyminer.core.db._
import cz.vse.easyminer.core.util.Lazy._
import cz.vse.easyminer.data.impl.db.hive.HiveDataSourceOps
import cz.vse.easyminer.data.impl.db.mysql.Tables.{DataSourceTable, FieldNumericDetailTable, FieldTable, InstanceTable => DataInstanceTable, ValueTable => DataValueTable}
import cz.vse.easyminer.preprocessing.impl.db.hive.HiveDatasetOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlSchemaOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeNumericDetailTable, AttributeTable, DatasetTable, InstanceTable => PreprocessingInstanceTable, ValueTable => PreprocessingValueTable}
import scalikejdbc._

object DBSpec extends ConfOpt {

  lazy val dbConnectors = new DBConnectors {

    private val limitedConnector = lazily {
      implicit val connector = new MysqlDBConnector(mysqlUserDatabase)
      //rollbackMysql()
      val schemaOps = MysqlSchemaOps()
      if (!schemaOps.schemaExists) schemaOps.createSchema()
      connector
    }

    private val unlimitedConnector = lazily {
      implicit val hiveConnector = new HiveDBConnector(hiveUserDatabase)
      implicit val mysqlConnector = limitedConnector()
      //rollbackHive()
      hiveConnector
    }

    def connector[A <: DBConnector[_]](dbType: DBType): A = dbType match {
      case LimitedDBType => limitedConnector().asInstanceOf[A]
      case UnlimitedDBType => unlimitedConnector().asInstanceOf[A]
    }

    def close(): Unit = {
      if (limitedConnector.isEvaluated) limitedConnector.close()
      if (unlimitedConnector.isEvaluated) unlimitedConnector.close()
    }

  }

  def rollbackHive()(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector) = {
    val datasetOps = HiveDatasetOps()
    for (dataset <- datasetOps.getAllDatasets) {
      datasetOps.deleteDataset(dataset.id)
    }
    val dataSourceOps = HiveDataSourceOps()
    for (dataSource <- dataSourceOps.getAllDataSources) {
      dataSourceOps.deleteDataSource(dataSource.id)
    }
  }

  def rollbackMysql()(implicit connector: MysqlDBConnector) = connector.DBConn autoCommit { implicit session =>
    def dropInstanceTable(table: SQLSyntaxSupport[_], tableIdColumn: SQLSyntax, tableIdToIntanceTable: Int => SQLSyntaxSupport[_], tableIdToValueTable: Int => SQLSyntaxSupport[_]) = sql"SHOW TABLES LIKE ${table.tableName}".map(_ => true).first().apply match {
      case Some(true) =>
        sql"SELECT $tableIdColumn FROM ${table.table}".foreach { rs =>
          val dataSourceId = rs.int(tableIdColumn.value)
          sql"DELETE FROM ${table.table} WHERE $tableIdColumn = $dataSourceId".execute().apply()
          val dataTable = tableIdToIntanceTable(dataSourceId)
          val valueTable = tableIdToValueTable(dataSourceId)
          sql"DROP TABLE IF EXISTS ${dataTable.table}".execute().apply()
          sql"DROP TABLE IF EXISTS ${valueTable.table}".execute().apply()
        }
      case _ =>
    }
    dropInstanceTable(DataSourceTable, DataSourceTable.column.id, id => new DataInstanceTable(id), id => new DataValueTable(id))
    sql"DROP TABLE IF EXISTS ${FieldNumericDetailTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${FieldTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${DataSourceTable.table}".execute().apply()
    dropInstanceTable(DatasetTable, DatasetTable.column.id, id => new PreprocessingInstanceTable(id), id => new PreprocessingValueTable(id))
    sql"DROP TABLE IF EXISTS ${AttributeNumericDetailTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${AttributeTable.table}".execute().apply()
    sql"DROP TABLE IF EXISTS ${DatasetTable.table}".execute().apply()
  }

}