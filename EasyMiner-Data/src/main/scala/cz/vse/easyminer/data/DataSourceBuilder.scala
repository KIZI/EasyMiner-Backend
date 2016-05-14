package cz.vse.easyminer.data


/**
 * Created by propan on 26. 7. 2015.
 */
sealed trait DataBuilder

trait DataSourceBuilder extends DataBuilder {

  val name: String

  def build(f: FieldBuilder => DataSourceDetail): DataSourceDetail

}

trait FieldBuilder extends DataBuilder {

  val dataSource: DataSourceDetail

  def field(field: Field): FieldBuilder

  def build(f: ValueBuilder => DataSourceDetail): DataSourceDetail

}

trait ValueBuilder extends DataBuilder {

  val dataSource: DataSourceDetail

  val fields: Seq[FieldDetail]

  def addInstance(values: Seq[Value]): ValueBuilder

  def build: DataSourceDetail

}
