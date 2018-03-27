package cz.vse.easyminer.preprocessing.impl.db.hive

import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{InstanceTable => MysqlInstanceTable}
import cz.vse.easyminer.preprocessing.impl.db.mysql.{Tables => MysqlTables}
import scalikejdbc._

/**
  * Created by propan on 9. 8. 2015.
  */
object Tables {

  val tablePrefix = MysqlTables.tablePrefix

  class InstanceTable(datasetId: Int) extends MysqlInstanceTable(datasetId)

  class ValueTable(datasetId: Int) extends SQLSyntaxSupport[ValueDetail] {

    override val tableName = s"${tablePrefix}value_$datasetId"

    override val columns = Seq("id", "attribute", "value", "frequency")

    def apply(m: ResultName[ValueDetail])(rs: WrappedResultSet): ValueDetail = ValueDetail(rs.int(m.id), rs.int(m.attribute), rs.string(m.value), rs.int(m.frequency))

  }

}