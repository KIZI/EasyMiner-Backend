/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.rest

import akka.actor.ActorContext
import cz.vse.easyminer.core.TaskStatusRestHelper
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.JsonFormatters
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, _}
import spray.routing.Directives

/**
 * Created by Vaclav Zeman on 1. 9. 2015.
 */
class ValueService(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail)(implicit dBConnectors: DBConnectors, actorContext: ActorContext)
  extends Directives
  with SprayJsonSupport
  with DefaultJsonProtocol
  with TaskStatusRestHelper
  with PreprocessingMainService.BaseUriPath {

  import JsonFormatters.JsonValueDetail._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._

  lazy val valueOps: ValueOps = datasetDetail.`type`.toValueOps(datasetDetail, attributeDetail)

  lazy val route = path("values") {
    parameters("offset".as[Int], "limit".as[Int]) { (offset, limit) =>
      complete(valueOps.getValues(offset, limit).toJson.asInstanceOf[JsArray])
    }
  }

}
