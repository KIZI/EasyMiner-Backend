/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.rest

import akka.actor.ActorContext
import cz.vse.easyminer.core._
import cz.vse.easyminer.core.db._
import cz.vse.easyminer.data.LimitedDataSourceType
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.JsonFormatters
import cz.vse.easyminer.preprocessing.rest.DatasetService.Exceptions.DataSourceNotFound
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, _}
import spray.routing.Directives

/**
  * Created by Vaclav Zeman on 19. 8. 2015.
  */

/**
  * This handles requests for dataset operations
  *
  * @param dBConnectors database connections
  * @param actorContext user actor context
  */
class DatasetService(implicit dBConnectors: DBConnectors, actorContext: ActorContext)
  extends Directives
  with SprayJsonSupport
  with DefaultJsonProtocol
  with TaskStatusRestHelper
  with PreprocessingMainService.BaseUriPath {

  import JsonFormatters.JsonDatasetDetail._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._
  import cz.vse.easyminer.data.impl.db.DataSourceTypeConversions._

  val datasetOps: DatasetOps = LimitedDatasetType.toDatasetOps

  private def routeAttribute(datasetDetail: DatasetDetail) = new AttributeService(datasetDetail).route

  lazy val route = pathEnd {
    get {
      complete(datasetOps.getAllDatasets.toJson.asInstanceOf[JsArray])
    } ~ post {
      formFields("dataSource".as[Int], "name") { (dataSource, name) =>
        requestUri { implicit uri =>
          val dataset = LimitedDataSourceType.toDataSourceOps.getDataSource(dataSource).map(x => Dataset(name, x)).getOrElse(throw new DataSourceNotFound(dataSource))
          val taskStatus = TaskStatusProcessor("Dataset creation.") { implicit tsp =>
            DatasetType(dataset.dataSourceDetail.`type`).toDatasetBuilder(dataset).build
          }
          completeAcceptedTaskStatus(taskStatus)
        }
      }
    }
  } ~ pathPrefix(IntNumber) { id =>
    datasetOps.getDataset(id) match {
      case Some(dataset) =>
        val datasetOps = dataset.`type`.toDatasetOps
        pathEnd {
          get {
            complete(dataset.toJson.asJsObject)
          } ~ delete {
            complete {
              datasetOps.deleteDataset(dataset.id)
              ""
            }
          } ~ put {
            entity(as[String]) { newName =>
              complete {
                datasetOps.renameDataset(dataset.id, newName)
                ""
              }
            }
          }
        } ~ routeAttribute(dataset)
      case None => reject
    }
  }

}

object DatasetService {

  object Exceptions {

    class DataSourceNotFound(dataSourceId: Int) extends Exception(s"Data source with ID $dataSourceId does not exist.") with StatusCodeException.NotFound

  }

}