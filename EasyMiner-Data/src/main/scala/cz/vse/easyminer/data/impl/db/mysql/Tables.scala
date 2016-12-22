package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.PersistentLock
import cz.vse.easyminer.core.util.Conf
import cz.vse.easyminer.data._
import scalikejdbc._

/**
  * Created by propan on 9. 8. 2015.
  */
object Tables {

  val tablePrefix = Conf().getOrElse("easyminer.data.table-prefix", "")

  object DataSourceTable extends SQLSyntaxSupport[DataSourceDetail] {

    override val tableName = tablePrefix + "data_source"

    override val columns = Seq("id", "name", "type", "size", "active")

    def apply(m: ResultName[DataSourceDetail])(rs: WrappedResultSet) = DataSourceDetail(
      rs.int(m.id),
      rs.string(m.name),
      rs.string(m.`type`) match {
        case "LIMITED" => LimitedDataSourceType
        case "UNLIMITED" => UnlimitedDataSourceType
      },
      rs.int(m.size),
      rs.boolean(m.active)
    )

  }

  object FieldTable extends SQLSyntaxSupport[FieldDetail] {

    override val tableName = tablePrefix + "field"

    override val columns = Seq("id", "data_source", "name", "type", "unique_values_size")

    val nominalName = "NOMINAL"
    val numericName = "NUMERIC"

    def apply(m: ResultName[FieldDetail])(rs: WrappedResultSet) = FieldDetail(
      rs.int(m.id),
      rs.int(m.dataSource),
      rs.string(m.name),
      rs.string(m.`type`) match {
        case `nominalName` => NominalFieldType
        case `numericName` => NumericFieldType
      },
      rs.int(m.uniqueValuesSize)
    )

  }

  object FieldNumericDetailTable extends SQLSyntaxSupport[FieldNumericDetail] {

    override val tableName = tablePrefix + "field_numeric_detail"

    override val columns = Seq("id", "min", "max", "avg")

    def apply(m: ResultName[FieldNumericDetail])(rs: WrappedResultSet): FieldNumericDetail = FieldNumericDetail(
      rs.int(m.id),
      rs.double(m.min),
      rs.double(m.max),
      rs.double(m.avg)
    )

  }

  class ValueTable(dataSourceId: Int) extends SQLSyntaxSupport[ValueDetail] {

    override val tableName = tablePrefix + "value_" + dataSourceId

    override val columns = Seq("id", "field", "value_nominal", "value_numeric", "frequency")

    def apply(m: ResultName[ValueDetail], fieldType: FieldType)(rs: WrappedResultSet): ValueDetail = {
      val value: Option[ValueDetail] = fieldType match {
        case NominalFieldType => rs.stringOpt(m.field("value_nominal")).map(value => NominalValueDetail(rs.int(m.id), rs.int(m.field("field")), value, rs.int(m.frequency)))
        case NumericFieldType => rs.doubleOpt(m.field("value_numeric")).map(value => NumericValueDetail(rs.int(m.id), rs.int(m.field("field")), rs.string(m.field("value_numeric")), value, rs.int(m.frequency)))
      }
      value.getOrElse(NullValueDetail(rs.int(m.id), rs.int(m.field("field")), rs.int(m.frequency)))
    }

  }

  class InstanceTable(dataSourceId: Int) extends SQLSyntaxSupport[NarrowInstance] {

    override val tableName = tablePrefix + "data_source_" + dataSourceId

    override val columns = Seq("id", "field", "value_nominal", "value_numeric")

    def apply(m: ResultName[NarrowInstance])(rs: WrappedResultSet)(implicit fieldIdToField: Int => FieldDetail): NarrowInstance = {
      val field: FieldDetail = rs.int(m.field("field"))
      field.`type` match {
        case NominalFieldType => NominalNarrowInstance(rs.int(m.id), field.id, NominalValue(rs.string(m.field("value_nominal"))))
        case NumericFieldType => NumericNarrowInstance(rs.int(m.id), field.id, NumericValue(rs.string(m.field("value_nominal")), rs.double(m.field("value_numeric"))))
      }
    }

  }

  object LockTable extends SQLSyntaxSupport[PersistentLock] {

    override def tableName: String = tablePrefix + "lock"

    override def columns: Seq[String] = List("name", "refresh_time")

  }

}