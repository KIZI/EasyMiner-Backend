/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.rest

import akka.actor.ActorContext
import cz.vse.easyminer.core.db._
import cz.vse.easyminer.core.util.AnyToInt
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.JsonFormatters
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, _}
import spray.routing.Directives

/**
  * Created by Vaclav Zeman on 19. 8. 2015.
  */

/**
  * This handles requests for operations with data source
  *
  * @param dBConnectors database connectors
  * @param actorContext user actor context
  */
class DataSourceService(implicit dBConnectors: DBConnectors, actorContext: ActorContext) extends Directives with SprayJsonSupport with DefaultJsonProtocol {

  import JsonFormatters.JsonDataSourceDetail._
  import JsonFormatters.JsonAggregatedInstance._
  import cz.vse.easyminer.data.impl.db.DataSourceTypeConversions._

  /**
    * Get object of data source operations
    */
  val dataSourceOps: DataSourceOps = LimitedDataSourceType.toDataSourceOps

  /**
    * Create field service from data source detail
    *
    * @param dataSourceDetail data source detail
    * @return field service route rules
    */
  private def routeField(dataSourceDetail: DataSourceDetail) = new FieldService(dataSourceDetail).route

  lazy val route = pathEnd {
    complete(dataSourceOps.getAllDataSources.toJson.asInstanceOf[JsArray])
  } ~ pathPrefix(IntNumber) { id =>
    dataSourceOps.getDataSource(id) match {
      case Some(dataSource) =>
        val dataSourceOps = dataSource.`type`.toDataSourceOps
        pathEnd {
          get {
            complete(dataSource.toJson.asJsObject)
          } ~ delete {
            complete {
              dataSourceOps.deleteDataSource(dataSource.id)
              ""
            }
          } ~ put {
            entity(as[String]) { newName =>
              complete {
                dataSourceOps.renameDataSource(dataSource.id, newName)
                ""
              }
            }
          }
        } ~ path("instances") {
          parameters("offset".as[Int], "limit".as[Int]) { (offset, limit) =>
            parameterSeq { pseq =>
              val fieldIds = pseq.collect {
                case ("field", AnyToInt(id)) => id
              }
              complete(dataSourceOps.getAggregatedInstances(dataSource.id, fieldIds, offset, limit))
            }
          }
        } ~ routeField(dataSource)
      case None => reject
    }
  }

}