/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.rest

import akka.actor.ActorContext
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.core.rest.CodeMessageRejection
import cz.vse.easyminer.data.impl.JsonFormatters
import cz.vse.easyminer.data.{DataSourceDetail, FieldDetail, FieldOps}
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, _}
import spray.routing.Directives

/**
  * Created by Vaclav Zeman on 20. 8. 2015.
  */

/**
  * This handles requests for operations with a field of a data source
  *
  * @param dataSourceDetail data source
  * @param dBConnectors     database connections
  * @param actorContext     user actor context
  */
class FieldService(dataSourceDetail: DataSourceDetail)(implicit dBConnectors: DBConnectors, actorContext: ActorContext) extends Directives with SprayJsonSupport with DefaultJsonProtocol {

  import JsonFormatters.JsonFieldDetail._
  import cz.vse.easyminer.data.impl.db.DataSourceTypeConversions._

  lazy val fieldOps: FieldOps = dataSourceDetail.`type`.toFieldOps(dataSourceDetail)

  private def routeValue(fieldDetail: FieldDetail) = new ValueService(dataSourceDetail, fieldDetail).route

  lazy val route = pathPrefix("field") {
    pathEnd {
      complete(fieldOps.getAllFields.toJson.asInstanceOf[JsArray])
    } ~ pathPrefix(IntNumber) { fieldId =>
      fieldOps.getField(fieldId) match {
        case Some(field) => pathEnd {
          get {
            complete(field)
          } ~ delete {
            complete {
              fieldOps.deleteField(fieldId)
              ""
            }
          } ~ put {
            entity(as[String]) { newName =>
              complete {
                fieldOps.renameField(fieldId, newName)
                ""
              }
            }
          }
        } ~ path("change-type") {
          put {
            dynamic {
              if (fieldOps.changeFieldType(field.id)) {
                complete("")
              } else {
                reject(CodeMessageRejection(StatusCodes.BadRequest, "The field type cannot be changed."))
              }
            }
          }
        } ~ routeValue(field)
        case None => reject
      }
    }
  }

}
