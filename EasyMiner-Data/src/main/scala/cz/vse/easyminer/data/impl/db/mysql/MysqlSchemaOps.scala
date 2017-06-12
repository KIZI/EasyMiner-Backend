/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db.mysql

import java.io.StringReader

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.{ScriptRunner, Template}
import cz.vse.easyminer.data.SchemaOps
import cz.vse.easyminer.data.impl.db.mysql.Tables.{DataSourceTable, FieldNumericDetailTable, FieldTable}
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 24. 8. 2015.
  */

/**
  * Implementation of database schema control on mysql database
  *
  * @param connector implicit! mysql db connector
  */
class MysqlSchemaOps(implicit connector: MysqlDBConnector) extends SchemaOps {

  import connector._

  /**
    * It checks whether database schema exists
    *
    * @return true = schema exists
    */
  def schemaExists: Boolean = DBConn readOnly { implicit session =>
    sql"SHOW TABLES LIKE ${DataSourceTable.tableName}".map(_ => true).first().apply.getOrElse(false)
  }

  /**
    * Create database schema
    */
  def createSchema(): Unit = DBConn autoCommit { implicit session =>
    try {
      tryClose(new StringReader(Template("data/metadataSchema.mustache", Map("prefix" -> Tables.tablePrefix))))(new ScriptRunner(session.connection, false, true).runScript)
    } catch {
      case ex: Throwable =>
        sql"DROP TABLE IF EXISTS ${FieldNumericDetailTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${FieldTable.table}".execute().apply()
        sql"DROP TABLE IF EXISTS ${DataSourceTable.table}".execute().apply()
        throw ex
    }
  }

}
