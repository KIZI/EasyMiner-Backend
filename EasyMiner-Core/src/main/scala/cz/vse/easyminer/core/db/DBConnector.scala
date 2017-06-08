/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.db

import java.io.Closeable

import cz.vse.easyminer.core.UserDatabase

/**
  * Basic traits for database connections
  * Created by Vaclav Zeman on 16. 8. 2015.
  */

/**
  * Every database connection has the type of connection DBConn, and basic settings for creation of this connection
  *
  * @tparam T Connection type
  */
trait DBConnector[T] extends Closeable {

  val dbSettings: UserDatabase

  def DBConn: T

}

object DBConnector {

  object Exceptions {

    object NoDatabaseSchema extends Exception("Database schema has not been created yet.")

    object UnknownDBConnector extends Exception("Unknown database connector.")

    class NoDbConnection(dbType: DBType) extends Exception("No database connection for type: " + dbType.getClass.getSimpleName)

  }

}

/**
  * This trait can contain DB connections of two types (limited and unlimited)
  */
trait DBConnectors extends Closeable {

  /**
    * Return a database connection for a specific database type
    *
    * @param dbType database type (limited or unlimited)
    * @tparam A desired connection type
    * @return database connector
    */
  def connector[A <: DBConnector[_]](dbType: DBType): A

}

/**
  * Database type
  */
sealed trait DBType

/**
  * Limited database type is for fast and real-time process, but it is limited by data size
  */
object LimitedDBType extends DBType

/**
  * Unlimited database type is for distributed process of big data, it is slow and is not for real-time usage, but it can handle to process very big data without limits.
  */
object UnlimitedDBType extends DBType
