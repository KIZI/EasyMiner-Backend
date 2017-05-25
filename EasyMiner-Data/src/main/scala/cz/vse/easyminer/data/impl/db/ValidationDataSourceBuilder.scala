/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.{StatusCodeException, Validator}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.Validators.{DataSourceValidators, FieldValidators, ValueValidators}
import cz.vse.easyminer.data.impl.db.ValidationDataSourceBuilder.Exceptions

/**
  * Created by Vaclav Zeman on 18. 8. 2015.
  */
class ValidationDataSourceBuilder(dataSourceBuilder: DataSourceBuilder) extends DataSourceBuilder with FieldValidators with DataSourceValidators with ValueValidators {

  val name: String = dataSourceBuilder.name

  class ValidationFieldBuilder(fieldBuilder: FieldBuilder, fieldSize: Int) extends FieldBuilder {

    val dataSource: DataSourceDetail = fieldBuilder.dataSource

    def build(f: (ValueBuilder) => DataSourceDetail): DataSourceDetail = {
      if (fieldSize == 0) {
        throw Exceptions.NoDataException
      }
      fieldBuilder.build { valueBuilder =>
        if (fieldSize != valueBuilder.fields.size) {
          throw new Exceptions.NumberOfFieldsException(valueBuilder.fields.size, fieldSize)
        }
        f(new ValidationValueBuilder(valueBuilder))
      }
    }

    def field(field: Field): FieldBuilder = {
      Validator(field)
      new ValidationFieldBuilder(fieldBuilder.field(field), fieldSize + 1)
    }

  }

  class ValidationValueBuilder(valueBuilder: ValueBuilder) extends ValueBuilder {

    val dataSource: DataSourceDetail = valueBuilder.dataSource

    val fields: Seq[FieldDetail] = valueBuilder.fields

    def addTransaction(itemset: Set[(FieldDetail, Value)]): ValueBuilder = {
      itemset.foreach(x => Validator(x._2))
      if (itemset.isEmpty) {
        throw Exceptions.NoDataException
      }
      new ValidationValueBuilder(valueBuilder.addTransaction(itemset))
    }

    override def addInstance(values: Seq[Value]): ValueBuilder = {
      if (values.size != fields.size) {
        throw new Exceptions.NumberOfValuesException(values.size, fields.size)
      }
      super.addInstance(values)
    }

    def build: DataSourceDetail = valueBuilder.build

  }

  def build(f: (FieldBuilder) => DataSourceDetail): DataSourceDetail = {
    Validator(DataSource(name, LimitedDataSourceType))
    dataSourceBuilder.build { fieldBuilder =>
      f(new ValidationFieldBuilder(fieldBuilder, 0))
    }
  }

}

object ValidationDataSourceBuilder {

  object Exceptions {

    sealed abstract class DataSourceBuilderException(msg: String) extends Exception("Invalid input data during building of a data source: " + msg) with StatusCodeException.BadRequest

    object NoDataException extends DataSourceBuilderException("Empty fields or instances can not be saved.")

    class NumberOfValuesException(num: Int, expected: Int) extends DataSourceBuilderException(s"Number of values ($num) does not equal to number of fields ($expected).")

    class NumberOfFieldsException(num: Int, expected: Int) extends DataSourceBuilderException(s"Number of created fields ($num) does not equal to number of received fields ($expected).")

  }

}
