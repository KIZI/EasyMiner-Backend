package cz.vse.easyminer.data.impl.db.hive

import cz.vse.easyminer.data.impl.db.mysql.Tables.{InstanceTable => MysqlInstanceTable}
import cz.vse.easyminer.data.impl.db.mysql.{Tables => MysqlTables}
import cz.vse.easyminer.data._
import scalikejdbc._

/**
 * Created by propan on 1. 11. 2015.
 */
object Tables {

  val tablePrefix = MysqlTables.tablePrefix

  class InstanceTable(dataSourceId: Int) extends MysqlInstanceTable(dataSourceId)

  class ValueTable(dataSourceId: Int) extends SQLSyntaxSupport[ValueDetail] {

    override val tableName = s"${tablePrefix}value_$dataSourceId"

    override val columns = Seq("field", "value_nominal", "value_numeric", "frequency", "rank")

    def apply(m: ResultName[ValueDetail], fieldType: FieldType)(rs: WrappedResultSet): ValueDetail = {
      val fieldId = rs.int(m.field("field"))
      val rank = rs.int(m.rank)
      val frequency = rs.int(m.frequency)
      val id = (fieldId.toString + rank).toInt
      val value: Option[ValueDetail] = fieldType match {
        case NominalFieldType => rs.stringOpt(m.valueNominal).map(value => NominalValueDetail(id, fieldId, value, frequency))
        case NumericFieldType => rs.doubleOpt(m.valueNumeric).map(value => NumericValueDetail(id, fieldId, rs.string(m.valueNominal), value, frequency))
      }
      value.getOrElse(NullValueDetail(id, fieldId, frequency))
    }

  }

}

