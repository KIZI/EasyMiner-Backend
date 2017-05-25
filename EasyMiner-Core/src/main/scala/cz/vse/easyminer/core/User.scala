/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core

/**
 * Created by Vaclav Zeman on 8. 8. 2015.
 */
case class User(id: Int, name: String, email: String)

trait UserDatabase {
  val dbServer: String
  val dbName: String
}

case class MysqlUserDatabase(dbServer: String, dbName: String, dbUser: String, dbPassword: String) extends UserDatabase

case class HiveUserDatabase(dbServer: String, dbPort: Int, dbName: String, dbUser: String) extends UserDatabase