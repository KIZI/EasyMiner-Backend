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
 * Created by propan on 20. 9. 2015.
 */
class MysqlDependencyChecker(val innerDependencyCheckers: Option[(InnerInput) => DependencyChecker.Runner] = None)(implicit ec: ExecutionContext) extends DependencyChecker[InnerInput] {

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
