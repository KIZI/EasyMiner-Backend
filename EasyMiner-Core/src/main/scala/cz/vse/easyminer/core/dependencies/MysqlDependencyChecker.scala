/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.dependencies

import akka.util.Timeout
import cz.vse.easyminer.core.DependencyChecker.DependecyCheckerException
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.dependencies.MysqlDependencyChecker.InnerInput
import cz.vse.easyminer.core.util.Concurrency._
import cz.vse.easyminer.core.util.Conf
import cz.vse.easyminer.core.{DependencyChecker, MysqlUserDatabase}
import scalikejdbc.SQL

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Created by Vaclav Zeman on 20. 9. 2015.
  */

/**
  * It checks, whether required database is accessible
  *
  * @param innerDependencyCheckers inner depencency checkers which required a database connection
  * @param ec                      execution context for asynchronous process (it is for timeout checking)
  */
class MysqlDependencyChecker(val innerDependencyCheckers: Option[(InnerInput) => DependencyChecker.Runner] = None)(implicit ec: ExecutionContext) extends DependencyChecker[InnerInput] {

  /**
    * This checks availibility of a database and all inner checkers. If some is not available then it throws exception
    */
  def check(): Unit = {
    val dBConnector = Future {
      val dBConnector = new MysqlDBConnector(
        MysqlUserDatabase(
          Conf().get[String]("easyminer.dependency-checker.mysql.server"),
          Conf().get[String]("easyminer.dependency-checker.mysql.db"),
          Conf().get[String]("easyminer.dependency-checker.mysql.user"),
          Conf().get[String]("easyminer.dependency-checker.mysql.password")
        )
      )
      dBConnector.DBConn readOnly { implicit session =>
        SQL("select 1 as one").map(x => true).list().apply()
      } match {
        case true :: Nil => dBConnector
        case _ => throw new DependecyCheckerException(this)
      }
    }.quickResultWithTimeout(Timeout(10 seconds))
    innerDependencyCheckers.foreach(_ (new InnerInput(dBConnector)).check())
  }

}

object MysqlDependencyChecker {

  class InnerInput(val dBConnector: MysqlDBConnector)

}
