package cz.vse.easyminer.miner.rest

import java.util.UUID

import akka.actor.ActorContext
import akka.util.Timeout
import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.core._
import cz.vse.easyminer.core.db.{DBConnectors, MysqlDBConnector}
import cz.vse.easyminer.miner.OutliersTask
import cz.vse.easyminer.miner.impl.JsonFormatters.JsonOutliersTask._
import cz.vse.easyminer.miner.impl.JsonFormatters.JsonOutlierWithInstance._
import cz.vse.easyminer.miner.impl.mysql.MysqlOutlierDetection
import cz.vse.easyminer.miner.impl.r.FpOutlierDetection
import cz.vse.easyminer.preprocessing.LimitedDatasetType
import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.limitedDatasetTypeToMysqlDatasetTypeOps
import spray.http.{HttpHeaders, StatusCodes}
import spray.httpx.SprayJsonSupport
import spray.json.{RootJsonFormat, _}
import spray.routing.Directives

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success, Try}

/**
  * Created by propan on 3. 3. 2017.
  */
class OutlierDetectionService(implicit actorContext: ActorContext, dBConnectors: DBConnectors) extends Directives with DefaultJsonProtocol with SprayJsonSupport {

  case class OutlierDetectionTaskRequest(datasetId: Int, minSupport: Double)

  implicit private val ec = actorContext.dispatcher
  implicit private val JsonOutlierDetectionTaskRequestFormat: RootJsonFormat[OutlierDetectionTaskRequest] = jsonFormat2(OutlierDetectionTaskRequest)
  implicit private val timeout = Timeout(5 seconds)
  implicit private lazy val mysqlDBConnector: MysqlDBConnector = dBConnectors

  Future {
    Thread.sleep((Random.nextInt(20) + 10) * 1000)
    MysqlOutlierDetection.clearZombie { id =>
      List(FpOutlierDetection(id))
    }
  }

  private def getOutlierDetection(datasetId: Int) = Try {
    LimitedDatasetType.toDatasetOps.getDataset(datasetId).map(_.`type`) match {
      case Some(LimitedDatasetType) => FpOutlierDetection(datasetId)
      case Some(_) => throw new ValidationException("Outlier detection is not supported for this type of dataset.")
      case None => throw new ValidationException(s"Dataset, with id $datasetId, does not exist.")
    }
  }

  lazy val route = pathPrefix("outlier-detection") {
    pathPrefix("result" / IntNumber) { datasetId =>
      getOutlierDetection(datasetId).toOption match {
        case Some(outlierDetection) =>
          pathPrefix(IntNumber) { taskId =>
            outlierDetection.getTask(taskId) match {
              case Some(outliersTask) =>
                path("outliers") {
                  parameters('offset.as[Int], 'limit.as[Int]) { (offset, limit) =>
                    complete(outlierDetection.retrieveOutliers(taskId, offset, limit))
                  }
                } ~ pathEnd {
                  get {
                    complete(outliersTask)
                  } ~ delete {
                    complete {
                      outlierDetection.removeTask(taskId)
                      ""
                    }
                  }
                }
              case None => reject
            }
          } ~ pathEnd {
            complete(outlierDetection.getTasks)
          }
        case None => reject
      }
    } ~ pathEnd {
      post {
        entity(as[OutlierDetectionTaskRequest])(createTask)
      }
    } ~ path(JavaUUID)(getTask)
  }

  private def createTask(taskRequest: OutlierDetectionTaskRequest) = {
    val taskStatus = TaskStatusProcessor("OutlierDetection", false) { tsp =>
      getOutlierDetection(taskRequest.datasetId).get.searchOutliers(taskRequest.minSupport)
    }
    requestUri { uri =>
      val rurl = uri.withPath(uri.path / taskStatus.id.toString)
      complete(StatusCodes.Accepted, List(HttpHeaders.Location(rurl)), taskStatus.toJson(new JsonEmptyTaskStatusWriter(rurl)).asJsObject)
    }
  }

  private def getTask(id: UUID) = requestUri { uri =>
    TaskStatusProvider.status(id) {
      case Success(EmptyOrMessageTaskStatus(taskStatus)) => complete(StatusCodes.Accepted, taskStatus.toJson(new JsonEmptyOrMessageTaskStatusWriter(uri)).asJsObject)
      case Success(ResultTaskStatus(_, _, outliersTask: OutliersTask)) => complete(StatusCodes.Created, outliersTask)
      case Failure(ex) => throw ex
    }
  }

}
