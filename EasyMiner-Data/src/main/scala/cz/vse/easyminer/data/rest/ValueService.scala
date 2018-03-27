/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.rest

import akka.actor.ActorContext
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.core.{TaskStatusProcessor, TaskStatusRestHelper}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.JsonFormatters
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, _}
import spray.routing.Directives

/**
  * Created by Vaclav Zeman on 1. 9. 2015.
  */

/**
  * This handles requests for operations with values of a field and a data source
  *
  * @param dataSourceDetail data source
  * @param fieldDetail      field detail
  * @param dBConnectors     database connections
  * @param actorContext     user actor context
  */
class ValueService(dataSourceDetail: DataSourceDetail, fieldDetail: FieldDetail)(implicit dBConnectors: DBConnectors, actorContext: ActorContext)
  extends Directives
  with SprayJsonSupport
  with DefaultJsonProtocol
  with TaskStatusRestHelper
  with DataMainService.BaseUriPath {

  import JsonFormatters.JsonFieldNumericDetail._
  import JsonFormatters.JsonValueDetail._
  import cz.vse.easyminer.data.impl.db.DataSourceTypeConversions._

  lazy val valueOps: ValueOps = dataSourceDetail.`type`.toValueOps(dataSourceDetail, fieldDetail)

  private def specificBorder(inclusive: Boolean)(value: Double): IntervalBorder = if (inclusive) InclusiveIntervalBorder(value) else ExclusiveIntervalBorder(value)

  lazy val numericRoute = path("stats") {
    dynamic {
      valueOps.getNumericStats match {
        case Some(stats) => complete(stats)
        case None => reject
      }
    }
  } ~ path("aggregated-values") {
    parameters("bins".as[Int], "min".as[Double].?, "max".as[Double].?, "minInclusive".as[Boolean] ? true, "maxInclusive".as[Boolean] ? true) { (bins, min, max, minInc, maxInc) =>
      requestUri { implicit uri =>
        val taskStatus = TaskStatusProcessor("Histogram counting.") { implicit tsp =>
          valueOps.getHistogram(bins, min.map(specificBorder(minInc)), max.map(specificBorder(maxInc)))
        }
        //if request timeout it returns string message (request timeout) which can't be transformed to json; therefore the server returns a wrong error!
        completeAcceptedTaskStatus(taskStatus)
      }
    }
  }

  lazy val allRoute = path("values") {
    parameters("offset".as[Int], "limit".as[Int]) { (offset, limit) =>
      complete(valueOps.getValues(offset, limit).toJson.asInstanceOf[JsArray])
    }
  }

  lazy val route = fieldDetail.`type` match {
    case NumericFieldType => allRoute ~ numericRoute
    case NominalFieldType => allRoute
  }

}
