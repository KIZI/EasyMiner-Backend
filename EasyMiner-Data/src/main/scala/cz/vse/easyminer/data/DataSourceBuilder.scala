/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 26. 7. 2015.
  */

/**
  * Data source builder abstraction
  * This relates to data source building, field building and value building of a data source
  */
sealed trait DataBuilder

/**
  * Data source builder for creation of a data source and save it into a database
  */
trait DataSourceBuilder extends DataBuilder {

  /**
    * Data source name
    */
  val name: String

  /**
    * This function builds all.
    * First it creates an empty data source and then creates a field builder and uses "f" function for building of fields for the data source
    * The "f" function should create the final data source
    *
    * @param f function, which builds fields for a data source with a field builder
    * @return created data source detail
    */
  def build(f: FieldBuilder => DataSourceDetail): DataSourceDetail

}

/**
  * This builder creates fields for a data source
  */
trait FieldBuilder extends DataBuilder {

  /**
    * Data source detail (data source should be empty)
    */
  val dataSource: DataSourceDetail

  /**
    * Add a field to the data source
    *
    * @param field added field
    * @return new field builder with an added field
    */
  def field(field: Field): FieldBuilder

  /**
    * After you added all fields, then create value builder for populating all fields with data
    * Created value builder is delegated to "f" function, which should create the final data source
    *
    * @param f function, which populates values to fields for a data source
    * @return created data source detail
    */
  def build(f: ValueBuilder => DataSourceDetail): DataSourceDetail

}

/**
  * This builder populates value to fields for a data source
  */
trait ValueBuilder extends DataBuilder {

  /**
    * Data source detail (data source should be empty)
    */
  val dataSource: DataSourceDetail

  /**
    * Created fields for the data source
    */
  val fields: Seq[FieldDetail]

  /**
    * This adds one row of input data, within the input table, into transaction database.
    * The ordering of values must be same as the ordering of fields; each value is attached to a field with the same position.
    *
    * @param values row of values
    * @return new value builder with added data
    */
  def addInstance(values: Seq[Value]): ValueBuilder = addTransaction(fields.iterator.zip(values.iterator).toSet)

  /**
    * This adds one transaction into transaction database.
    * One transaction is an itemset which contains couples (field -> value)
    *
    * @param itemset one transaction of input data
    * @return new value builder with added data
    */
  def addTransaction(itemset: Set[(FieldDetail, Value)]): ValueBuilder

  /**
    * After you added all values, then create a final shape of the data source detail
    *
    * @return created data source detail
    */
  def build: DataSourceDetail

}