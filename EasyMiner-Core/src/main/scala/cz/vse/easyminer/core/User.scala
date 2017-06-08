/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core

/**
  * Class for user and user database
  * There are two supported database types, mysql = for fast and real-time mining, hive = for batch mining of big data
  * Created by Vaclav Zeman on 8. 8. 2015.
  */

/**
  * Basic user information
  *
  * @param id    user id
  * @param name  user name
  * @param email user email
  */
case class User(id: Int, name: String, email: String)

trait UserDatabase {
  val dbServer: String
  val dbName: String
}

case class MysqlUserDatabase(dbServer: String, dbName: String, dbUser: String, dbPassword: String) extends UserDatabase

case class HiveUserDatabase(dbServer: String, dbPort: Int, dbName: String, dbUser: String) extends UserDatabase