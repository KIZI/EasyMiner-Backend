package cz.vse.easyminer.miner.impl.mysql

import java.io.StringReader

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.{ScriptRunner, Template}
import cz.vse.easyminer.miner.impl.mysql.Tables.OutliersTaskTable
import cz.vse.easyminer.miner.{OutlierDetection, OutliersTask}
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.DatasetTable
import scalikejdbc._

/**
  * Created by propan on 1. 3. 2017.
  */
abstract class MysqlOutlierDetection(implicit mysqlDBConnector: MysqlDBConnector) extends OutlierDetection {

  import mysqlDBConnector.DBConn

  if (!schemaExists) {
    createSchema()
  }

  private def schemaExists: Boolean = DBConn readOnly { implicit session =>
    sql"SHOW TABLES LIKE ${OutliersTaskTable.tableName}".map(_ => true).first().apply.getOrElse(false)
  }

  private def createSchema(): Unit = DBConn autoCommit { implicit session =>
    try {
      tryClose(new StringReader(Template("metadataSchemaOutliers.mustache", Map("prefix" -> Tables.tablePrefix))))(new ScriptRunner(session.connection, false, true).runScript)
    } catch {
      case ex: Throwable =>
        sql"DROP TABLE IF EXISTS ${OutliersTaskTable.table}".execute().apply()
        throw ex
    }
  }

  protected def dropTaskTable(task: OutliersTask)

  def removeTask(id: Int): Unit = getTask(id).foreach { task =>
    dropTaskTable(task)
    DBConn autoCommit { implicit session =>
      sql"DELETE FROM ${OutliersTaskTable.table} WHERE ${OutliersTaskTable.column.id} = ${task.id}".execute().apply()
    }
  }

  def getTasks: Seq[OutliersTask] = DBConn readOnly { implicit session =>
    val o = OutliersTaskTable.syntax("o")
    sql"SELECT ${o.result.*} FROM ${OutliersTaskTable as o} WHERE ${o.dataset} = $datasetId".map(OutliersTaskTable(o.resultName)).list().apply()
  }

  def getTask(id: Int): Option[OutliersTask] = DBConn readOnly { implicit session =>
    val o = OutliersTaskTable.syntax("o")
    sql"SELECT ${o.result.*} FROM ${OutliersTaskTable as o} WHERE ${o.dataset} = $datasetId AND ${o.id} = $id".map(OutliersTaskTable(o.resultName)).first().apply()
  }

}

object MysqlOutlierDetection {

  def clearZombie(datasetIdToOutlierDetections: Int => Seq[OutlierDetection])(implicit mysqlDBConnector: MysqlDBConnector) = {
    import mysqlDBConnector.DBConn
    val o = OutliersTaskTable.syntax("o")
    val d = DatasetTable.syntax("d")
    val isolatedTasks = DBConn readOnly { implicit session =>
      sql"SELECT ${o.result.*} FROM ${OutliersTaskTable as o} LEFT JOIN ${DatasetTable as d} ON (${o.dataset} = ${d.id}) WHERE ${d.id} IS NULL".map(OutliersTaskTable(o.resultName)).list().apply()
    }
    for (isolatedTask <- isolatedTasks) {
      datasetIdToOutlierDetections(isolatedTask.dataset).foreach(_.removeTask(isolatedTask.id))
    }
  }

}
