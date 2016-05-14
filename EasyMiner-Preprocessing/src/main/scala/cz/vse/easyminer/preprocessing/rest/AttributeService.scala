package cz.vse.easyminer.preprocessing.rest

import akka.actor.ActorContext
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.core.rest.CodeMessageRejection
import cz.vse.easyminer.core.{TaskStatusProcessor, TaskStatusRestHelper}
import cz.vse.easyminer.preprocessing.impl.JsonFormatters
import cz.vse.easyminer.preprocessing.impl.parser.PmmlTaskParser
import cz.vse.easyminer.preprocessing.{AttributeDetail, AttributeOps, DatasetDetail, SimpleAttribute}
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, _}
import spray.routing.Directives

import scala.xml.NodeSeq

/**
 * Created by propan on 20. 8. 2015.
 */
class AttributeService(datasetDetail: DatasetDetail)(implicit dBConnectors: DBConnectors, actorContext: ActorContext)
  extends Directives
  with SprayJsonSupport
  with DefaultJsonProtocol
  with TaskStatusRestHelper
  with PreprocessingMainService.BaseUriPath {

  import JsonFormatters.JsonAttributeDetail._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._

  lazy val attributeOps: AttributeOps = datasetDetail.`type`.toAttributeOps(datasetDetail)

  private def routeValue(attributeDetail: AttributeDetail) = new ValueService(datasetDetail, attributeDetail).route

  lazy val route = pathPrefix("attribute") {
    pathEnd {
      get {
        complete(attributeOps.getAllAttributes.toJson.asInstanceOf[JsArray])
      } ~ post {
        entity(as[NodeSeq]) { pmml =>
          requestUri { implicit uri =>
            val attributes = new PmmlTaskParser(pmml).attributes
            if (attributes.isEmpty) {
              reject(CodeMessageRejection(StatusCodes.BadRequest, "No attribute has been parsed."))
            } else {
              val taskStatus = TaskStatusProcessor("Attribute/s creation.") { implicit tsp =>
                val simpleAttributes = attributes.collect {
                  case attribute: SimpleAttribute => attribute
                }
                val attributeDetails = for (attribute <- attributes if !attribute.isInstanceOf[SimpleAttribute] || simpleAttributes.size <= 1) yield {
                  datasetDetail.`type`.toAttributeBuilder(datasetDetail, attribute).build
                }
                if (simpleAttributes.size > 1) {
                  attributeDetails ++ datasetDetail.`type`.toCollectiveAttributeBuilder(datasetDetail, simpleAttributes: _*).build
                } else {
                  attributeDetails
                }
              }
              completeAcceptedTaskStatus(taskStatus)
            }
          }
        }
      }
    } ~ pathPrefix(IntNumber) { attributeId =>
      attributeOps.getAttribute(attributeId) match {
        case Some(attribute) => pathEnd {
          get {
            complete(attribute)
          } ~ delete {
            complete {
              attributeOps.deleteAttribute(attributeId)
              ""
            }
          } ~ put {
            entity(as[String]) { newName =>
              complete {
                attributeOps.renameAttribute(attributeId, newName)
                ""
              }
            }
          }
        } ~ routeValue(attribute)
        case None => reject
      }
    }
  }

}
