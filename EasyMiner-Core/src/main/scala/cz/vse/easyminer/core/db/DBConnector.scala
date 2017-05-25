/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.db

import java.io.Closeable

import cz.vse.easyminer.core.UserDatabase

/**
 * Created by Vaclav Zeman on 16. 8. 2015.
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

trait DBConnectors extends Closeable {

  def connector[A <: DBConnector[_]](dbType: DBType): A

}

sealed trait DBType

object LimitedDBType extends DBType

object UnlimitedDBType extends DBType
