package cz.vse.easyminer.preprocessing.rest

import akka.actor.ActorContext
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.core.{TaskStatusProcessor, TaskStatusRestHelper}
import cz.vse.easyminer.data.{ExclusiveIntervalBorder, InclusiveIntervalBorder, IntervalBorder}
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.JsonFormatters
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, _}
import spray.routing.Directives

/**
 * Created by propan on 1. 9. 2015.
 */
class ValueService(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail)(implicit dBConnectors: DBConnectors, actorContext: ActorContext)
  extends Directives
  with SprayJsonSupport
  with DefaultJsonProtocol
  with TaskStatusRestHelper
  with PreprocessingMainService.BaseUriPath {

  import JsonFormatters.JsonAttributeNumericDetail._
  import JsonFormatters.JsonValueDetail._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._

  lazy val valueOps: ValueOps = datasetDetail.`type`.toValueOps(datasetDetail, attributeDetail)

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
        completeAcceptedTaskStatus(taskStatus)
        //if request timeout it returns string message (request timeout) which can't be transformed to json; therefore the server returns a wrong error!
      }
    }
  }

  lazy val allRoute = path("values") {
    parameters("offset".as[Int], "limit".as[Int]) { (offset, limit) =>
      complete(valueOps.getValues(offset, limit).toJson.asInstanceOf[JsArray])
    }
  }

  lazy val route = attributeDetail.`type` match {
    case NumericAttributeType => allRoute ~ numericRoute
    case NominalAttributeType => allRoute
  }

}
