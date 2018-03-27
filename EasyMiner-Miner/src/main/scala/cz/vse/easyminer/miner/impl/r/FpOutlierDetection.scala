package cz.vse.easyminer.miner.impl.r

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.{Conf, Template}
import cz.vse.easyminer.miner._
import cz.vse.easyminer.miner.impl.mysql.MysqlOutlierDetection
import cz.vse.easyminer.miner.impl.mysql.Tables.{OutliersTable, OutliersTaskTable}
import cz.vse.easyminer.miner.impl.r.FpOutlierDetection.Exceptions.RScriptException
import cz.vse.easyminer.miner.impl.{RConnectionPoolImpl, ValidationOutlierDetection}
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlDatasetOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{InstanceTable, ValueTable}
import cz.vse.easyminer.preprocessing.{InstanceItemWithValue, InstanceWithValue}
import org.slf4j.LoggerFactory
import scalikejdbc._

/**
  * Created by propan on 28. 2. 2017.
  */
class FpOutlierDetection private(val datasetId: Int)
                                (implicit
                                 mysqlDBConnector: MysqlDBConnector,
                                 rConnectionPool: RConnectionPool)
  extends MysqlOutlierDetection {

  import mysqlDBConnector.DBConn

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.impl.r.FpOutlierDetection")

  private def createCompletedOutlierTask(outliersTempTable: OutliersTable): OutliersTask = {
    val taskId = DBConn autoCommit { implicit session =>
      val taskId = sql"INSERT INTO ${OutliersTaskTable.table} (${OutliersTaskTable.column.dataset}) VALUES ($datasetId)".updateAndReturnGeneratedKey().apply().toInt
      val outliersTable = new OutliersTable(datasetId, taskId)
      sql"RENAME TABLE ${outliersTempTable.table} TO ${outliersTable.table}".execute().apply()
      taskId
    }
    getTask(taskId).get
  }

  def searchOutliers(support: Double): OutliersTask = {
    val jdbcDriverAbsolutePath = Conf().get[String]("easyminer.miner.r.jdbc-driver-dir-absolute-path")
    val outliersTempTable = DBConn autoCommit { implicit session =>
      val outliersTable = Stream.continually(new OutliersTable()).find { outliersTable =>
        !sql"SHOW TABLES LIKE ${outliersTable.tableName}".map(_ => true).first().apply.getOrElse(false)
      }.get
      sql"""
      CREATE TABLE IF NOT EXISTS ${outliersTable.table} (
      `id` int(10) unsigned NOT NULL,
      `score` double NOT NULL,
      PRIMARY KEY (`id`),
      KEY `score` (`score`)
      ) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
      """.execute().apply()
      outliersTable
    }
    try {
      val instanceTable = new InstanceTable(datasetId)
      val rscript = Template(
        "ROutlierDetection.mustache",
        Map(
          "jdbcDriverAbsolutePath" -> jdbcDriverAbsolutePath,
          "dbServer" -> mysqlDBConnector.dbSettings.dbServer,
          "dbName" -> mysqlDBConnector.dbSettings.dbName,
          "dbUser" -> mysqlDBConnector.dbSettings.dbUser,
          "dbPassword" -> mysqlDBConnector.dbSettings.dbPassword,
          "dbTableName" -> instanceTable.tableName,
          "minSupport" -> support,
          "dbOutliersTableName" -> outliersTempTable.tableName
        )
      )
      RScript.eval(rscript).mkString("\n") match {
        case "[1] \"ok\"" => createCompletedOutlierTask(outliersTempTable)
        case rOutput =>
          logger.error(s"Unexpected result from R within the outlier detection task for dataset $datasetId: $rOutput")
          throw RScriptException
      }
    } finally {
      DBConn autoCommit { implicit session =>
        sql"DROP TABLE IF EXISTS ${outliersTempTable.table}".execute().apply()
      }
    }
  }

  def retrieveOutliers(id: Int, offset: Int, limit: Int): Seq[OutlierWithInstance] = {
    getTask(id).flatMap { outlierTask =>
      MysqlDatasetOps().getDataset(outlierTask.dataset).map { datasetDetail =>
        val outliersTable = new OutliersTable(datasetDetail.id, id)
        val scoreSubQuery = sqls"SELECT * FROM ${outliersTable.table} ORDER BY ${outliersTable.column.score} DESC LIMIT $limit OFFSET $offset"
        val instanceTable = new InstanceTable(datasetDetail.id)
        val valueTable = new ValueTable(datasetDetail.id)
        val d = instanceTable.syntax("d")
        val v = valueTable.syntax("v")
        DBConn readOnly { implicit session =>
          sql"""
             SELECT ${d.result.*}, ${v.result.value}, o.score
             FROM ($scoreSubQuery) o
             INNER JOIN ${instanceTable as d} ON (${d.id} = o.id)
             INNER JOIN ${valueTable as v} ON (${d.attribute} = ${v.attribute} AND ${d.value} = ${v.id})
            """.map(wrs => (wrs.double("score"), wrs.string(v.resultName.value), instanceTable(d.resultName)(wrs))).list().apply()
            .groupBy(_._3.id)
            .flatMap { case (id, instances) =>
              val instanceItemsWithValue = instances.map { case (_, value, instance) =>
                InstanceItemWithValue(instance.attribute, value)
              }
              instances.headOption.map(_._1).map { score =>
                OutlierWithInstance(score, InstanceWithValue(id, instanceItemsWithValue))
              }
            }
            .toList
            .sortBy(_.score)(Ordering[Double].reverse)
        }
      }
    }.getOrElse(Nil)
  }

  protected def dropTaskTable(task: OutliersTask): Unit = DBConn autoCommit { implicit session =>
    val outliersTable = new OutliersTable(task.dataset, task.id)
    sql"DROP TABLE IF EXISTS ${outliersTable.table}".execute().apply()
  }

}

object FpOutlierDetection {

  object Exceptions {

    object RScriptException extends Exception("Unexpected result from R within the outlier detection task.")

  }

  def apply(datasetId: Int)(implicit mysqlDBConnector: MysqlDBConnector): OutlierDetection = {
    implicit val rConnectionPool = RConnectionPoolImpl.defaultOutliers
    new ValidationOutlierDetection(new FpOutlierDetection(datasetId))
  }

}
