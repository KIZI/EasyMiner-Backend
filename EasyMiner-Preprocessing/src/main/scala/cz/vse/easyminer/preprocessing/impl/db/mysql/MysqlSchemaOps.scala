package cz.vse.easyminer.preprocessing.impl.db.mysql

import java.io.StringReader

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.{ScriptRunner, Template}
import cz.vse.easyminer.data.{SchemaOps => DataSchemaOps}
import cz.vse.easyminer.data.impl.db.mysql.{MysqlSchemaOps => DataMysqlSchemaOps}
import cz.vse.easyminer.preprocessing.impl.db.DbSchemaOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeNumericDetailTable, AttributeTable, DatasetTable}
import cz.vse.easyminer.preprocessing.{SchemaOps => PreprocessingSchemaOps}
import scalikejdbc._


/**
 * Created by propan on 21. 12. 2015.
 */
class MysqlSchemaOps(private[db] val dataSchemaOps: DataSchemaOps)(implicit connector: MysqlDBConnector) extends DbSchemaOps {

  import connector._

  private[db] def createPreprocessingSchema(): Unit = DBConn autoCommit { implicit session =>
    try {
      tryClose(new StringReader(Template("preprocessing/metadataSchema.mustache", Map("prefix" -> Tables.tablePrefix))))(new ScriptRunner(session.connection, false, true).runScript)
    } catch {
      case ex: Throwable =>
        sql"DROP TABLE IF EXISTS ${AttributeNumericDetailTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${AttributeTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${DatasetTable.table}".execute().apply()
        throw ex
    }
  }

  private[db] def preprocessingSchemaExists: Boolean = DBConn readOnly { implicit session =>
    sql"SHOW TABLES LIKE ${DatasetTable.tableName}".map(_ => true).first().apply.getOrElse(false)
  }

}

object MysqlSchemaOps {

  def apply()(implicit connector: MysqlDBConnector): PreprocessingSchemaOps = new MysqlSchemaOps(new DataMysqlSchemaOps())

}