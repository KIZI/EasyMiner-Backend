package cz.vse.easyminer.data

import cz.vse.easyminer.core.util.Conf

/**
 * Created by propan on 4. 2. 2016.
 */
trait ConfOpt {

  def dbserver = Conf().get[String]("db.server")

  def dbuser = Conf().get[String]("db.user")

  def dbpassword = Conf().get[String]("db.password")

  def dbname = Conf().get[String]("db.name")

  def hiveserver = Conf().get[String]("hive.server")

  def hiveuser = Conf().get[String]("hive.user")

  def hiveport = Conf().get[Int]("hive.port")

  def hivename = Conf().get[String]("hive.name")

}
