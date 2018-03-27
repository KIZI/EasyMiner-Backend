package cz.vse.easyminer.preprocessing.impl
package db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.{ExclusiveIntervalBorder, InclusiveIntervalBorder, IntervalBorder}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.{DatasetTypeConversions, DbMysqlTables, ValidationAttributeBuilder}
import cz.vse.easyminer.preprocessing.impl.db.IntervalBorderOps._
import eu.easyminer.discretization.algorithm.Discretization
import eu.easyminer.discretization.impl.sorting.SortedIterable
import eu.easyminer.discretization.impl.{ExclusiveIntervalBound, InclusiveIntervalBound, Interval, IntervalBound}
import scalikejdbc._

import scala.language.implicitConversions

/**
  * Created by propan on 13. 4. 2017.
  */
class MysqlDiscretizationAttributeBuilder[T <: Attribute] private(val dataset: DatasetDetail,
                                                                  val attributes: Seq[T])
                                                                 (implicit
                                                                  attributeToDiscretization: T => Discretization[Double],
                                                                  mysqlDBConnector: MysqlDBConnector,
                                                                  taskStatusProcessor: TaskStatusProcessor) extends AttributeBuilder[T] with DbMysqlTables {

  /**
    * It converts discretization interval bounds to local interval borders
    *
    * @param intervalBound discretization interval bound
    * @return local interval border
    */
  private implicit def discIntervalBoundToIntervalBorder(intervalBound: IntervalBound): IntervalBorder = intervalBound match {
    case InclusiveIntervalBound(x) => InclusiveIntervalBorder(x)
    case ExclusiveIntervalBound(x) => ExclusiveIntervalBorder(x)
  }

  /**
    * It converts discretization intervals to local intervals
    *
    * @param interval discretization interval
    * @return local interval
    */
  private def discIntervalToInterval(interval: Interval): NumericIntervalsAttribute.Interval = NumericIntervalsAttribute.Interval(interval.minValue, interval.maxValue)

  def build: Seq[AttributeDetail] = {
    import mysqlDBConnector._
    val intervals = DBConn readOnly { implicit session =>
      //dbconn init - we need to load all numerics from database (dataset - attribute)
      for (attribute <- attributes) yield {
        //for each attribute do discretization
        //some discretization algorithms require sorted iterable where numerics are sorted in ascending
        val it = new SortedIterable[Double] {
          //we load all numerics into memory
          //it can cause memory problem (e.g. 10M unique numbers take 80MB memory) - solution: stream
          lazy val data = {
            val dv = dataValueTable.syntax("dv")
            //where conditions: min-max borders and only numeric values without empty values
            val conds = attribute.features.collectFirst {
              case ib: IntervalsBorder => List(ib.min.map(_ |<=| sqls"${dv.valueNumeric}"), ib.max.map(_ |>=| sqls"${dv.valueNumeric}")).flatten.reduce(_ and _)
            }.foldLeft(sqls"${dv.field("field")} = ${attribute.field} AND ${dv.valueNumeric} IS NOT NULL")(_ and _)
            //db select query of numeric values for a field
            sql"SELECT ${dv.result.valueNumeric}, ${dv.result.frequency} FROM ${dataValueTable as dv} WHERE $conds ORDER BY ${dv.valueNumeric}".map { wrs =>
              wrs.double(dv.resultName.valueNumeric) -> wrs.int(dv.resultName.frequency)
            }.list().apply()
          }

          def iterator: Iterator[Double] = data.iterator.flatMap(x => Iterator.fill(x._2)(x._1))
        }
        //apply discretization algorithm
        //here is implicit conversion from attribute to discretization algorithm
        val intervals = attribute.discretize(it)
        //return interval enumeration input task with intervals
        NumericIntervalsAttribute(
          attribute.name,
          attribute.field,
          intervals.map(discIntervalToInterval).map(interval => NumericIntervalsAttribute.Bin(interval.toIntervalString, List(interval))),
          attribute.features
        )
      }
    }
    //apply interval enumeration method; this returns attribute builder
    MysqlNumericIntervalsAttributeBuilder(intervals, dataset).build
  }

}

object MysqlDiscretizationAttributeBuilder {

  import DatasetTypeConversions.Limited._

  def apply[T <: Attribute](attributes: Seq[T], datasetDetail: DatasetDetail)
                           (implicit attributeToDiscretization: T => Discretization[Double], mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) = new ValidationAttributeBuilder(
    new MysqlDiscretizationAttributeBuilder[T](datasetDetail, attributes)
  )

}