/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db.mysql

import java.io.StringReader

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.{ScriptRunner, Template}
import cz.vse.easyminer.data.impl.db.mysql.{MysqlSchemaOps => DataMysqlSchemaOps}
import cz.vse.easyminer.data.{SchemaOps => DataSchemaOps}
import cz.vse.easyminer.preprocessing.impl.db.DbSchemaOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{AttributeTable, DatasetTable}
import cz.vse.easyminer.preprocessing.{SchemaOps => PreprocessingSchemaOps}
import scalikejdbc._


/**
  * Created by Vaclav Zeman on 21. 12. 2015.
  */

/**
  * Class for creation mysql database schema for preprocessing tables
  *
  * @param dataSchemaOps schema builder for easyminer data module
  * @param connector     mysql database connection
  */
class MysqlSchemaOps private(protected val dataSchemaOps: DataSchemaOps)(implicit connector: MysqlDBConnector) extends DbSchemaOps {

  import connector._

  protected def createPreprocessingSchema(): Unit = DBConn autoCommit { implicit session =>
    try {
      tryClose(new StringReader(Template("preprocessing/metadataSchema.mustache", Map("prefix" -> Tables.tablePrefix))))(new ScriptRunner(session.connection, false, true).runScript)
    } catch {
      case ex: Throwable =>
        sql"DROP TABLE IF EXISTS ${AttributeTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${DatasetTable.table}".execute().apply()
        throw ex
    }
  }

  protected def preprocessingSchemaExists: Boolean = DBConn readOnly { implicit session =>
    sql"SHOW TABLES LIKE ${DatasetTable.tableName}".map(_ => true).first().apply.getOrElse(false)
  }

}

object MysqlSchemaOps {

  def apply()(implicit connector: MysqlDBConnector): PreprocessingSchemaOps = new MysqlSchemaOps(new DataMysqlSchemaOps())

}