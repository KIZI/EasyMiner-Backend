package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.util.Conf
import cz.vse.easyminer.preprocessing._
import scalikejdbc._

/**
  * Created by propan on 9. 8. 2015.
  */
object Tables {

  val tablePrefix = Conf().getOrElse("easyminer.preprocessing.table-prefix", "")

  object DatasetTable extends SQLSyntaxSupport[DatasetDetail] {

    override val tableName = tablePrefix + "dataset"

    override val columns = Seq("id", "name", "data_source", "type", "size", "active")

    def apply(m: ResultName[DatasetDetail])(rs: WrappedResultSet) = DatasetDetail(
      rs.int(m.id),
      rs.string(m.name),
      rs.int(m.dataSource),
      rs.string(m.`type`) match {
        case "LIMITED" => LimitedDatasetType
        case "UNLIMITED" => UnlimitedDatasetType
      },
      rs.int(m.size),
      rs.boolean(m.active)
    )

  }

  object AttributeTable extends SQLSyntaxSupport[AttributeDetail] {

    override val tableName = tablePrefix + "attribute"

    override val columns = Seq("id", "dataset", "field", "name", "type", "unique_values_size", "active")

    def apply(m: ResultName[AttributeDetail])(rs: WrappedResultSet) = AttributeDetail(
      rs.int(m.id),
      rs.string(m.name),
      rs.int(m.field("field")),
      rs.int(m.dataset),
      rs.string(m.`type`) match {
        case "NOMINAL" => NominalAttributeType
        case "NUMERIC" => NumericAttributeType
      },
      rs.int(m.uniqueValuesSize),
      rs.boolean(m.active)
    )

  }

  object AttributeNumericDetailTable extends SQLSyntaxSupport[AttributeNumericDetail] {

    override val tableName = tablePrefix + "attribute_numeric_detail"

    override val columns = Seq("id", "min", "max", "avg")

    def apply(m: ResultName[AttributeNumericDetail])(rs: WrappedResultSet): AttributeNumericDetail = AttributeNumericDetail(
      rs.int(m.id),
      rs.double(m.min),
      rs.double(m.max),
      rs.double(m.avg)
    )

  }

  class ValueTable(datasetId: Int) extends SQLSyntaxSupport[ValueDetail] {

    override val tableName = tablePrefix + "value_" + datasetId

    override val columns = Seq("id", "attribute", "value_nominal", "value_numeric", "frequency")

    def apply(m: ResultName[ValueDetail], fieldType: AttributeType)(rs: WrappedResultSet): ValueDetail = {
      val value: Option[ValueDetail] = fieldType match {
        case NominalAttributeType => rs.stringOpt(m.field("value_nominal")).map(value => NominalValueDetail(rs.int(m.id), rs.int(m.attribute), value, rs.int(m.frequency)))
        case NumericAttributeType => rs.doubleOpt(m.field("value_numeric")).map(value => NumericValueDetail(rs.int(m.id), rs.int(m.attribute), rs.string(m.field("value_nominal")), value, rs.int(m.frequency)))
      }
      value.getOrElse(NullValueDetail(rs.int(m.id), rs.int(m.attribute), rs.int(m.frequency)))
    }

  }

  class InstanceTable(datasetId: Int) extends SQLSyntaxSupport[NarrowInstance] {

    override def tableName = tablePrefix + "dataset_" + datasetId

    override val columns = Seq("id", "attribute", "value")

    def apply(m: ResultName[NarrowInstance])(rs: WrappedResultSet): NarrowInstance = NarrowInstance(rs.int(m.id), rs.int(m.attribute), rs.int(m.value))

  }

}