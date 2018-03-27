package cz.vse.easyminer.miner.impl.mysql

import cz.vse.easyminer.core.util.Conf
import cz.vse.easyminer.miner.{Outlier, OutliersTask}
import scalikejdbc._

import scala.util.Random

/**
  * Created by propan on 9. 8. 2015.
  */
object Tables {

  val tablePrefix = Conf().getOrElse("easyminer.miner.table-prefix", "")

  object OutliersTaskTable extends SQLSyntaxSupport[OutliersTask] {

    override val tableName = tablePrefix + "outliers_task"

    override val columns = Seq("id", "time", "dataset")

    def apply(m: ResultName[OutliersTask])(rs: WrappedResultSet) = OutliersTask(
      rs.int(m.id),
      rs.timestamp(m.time),
      rs.int(m.dataset)
    )

  }

  class OutliersTable private(tableSuffix: String) extends SQLSyntaxSupport[Outlier] {

    def this(datasetId: Int, taskId: Int) = this(datasetId + "_" + taskId)

    def this() = this("temp_" + Random.alphanumeric.take(5).mkString)

    override def tableName = tablePrefix + "outliers_" + tableSuffix

    override val columns = Seq("id", "score")

  }

}