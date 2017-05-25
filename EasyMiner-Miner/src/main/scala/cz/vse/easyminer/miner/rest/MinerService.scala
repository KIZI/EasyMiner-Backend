/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.rest

import java.util.{Date, UUID}

import akka.actor.{ActorContext, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.core.db.{MysqlDBConnector, DBConnectors}
import cz.vse.easyminer.core.util.RestUtils.PathExtension
import cz.vse.easyminer.core.{UnexpectedActorRequest, UnexpectedActorResponse}
import cz.vse.easyminer.miner.impl.r.AprioriMiner
import cz.vse.easyminer.miner.impl.{MinerTaskValidatorImpl, PmmlResult, PmmlTaskParser}
import cz.vse.easyminer.miner.{Miner, MinerResult, MinerTask, RScript}
import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._
import cz.vse.easyminer.preprocessing.{LimitedDatasetType, UnlimitedDatasetType}
import org.slf4j.LoggerFactory
import spray.http.MediaTypes._
import spray.http._
import spray.routing.Directives

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import scala.xml.NodeSeq

/**
  * Created by Vaclav Zeman on 22. 2. 2016.
  */
class MinerService(implicit actorContext: ActorContext, dBConnectors: DBConnectors) extends Directives {

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.rest.MinerService")

  implicit private val ec: ExecutionContext = actorContext.dispatcher
  implicit private val timeout = Timeout(5 seconds)
  implicit private lazy val mysqlDBConnector: MysqlDBConnector = dBConnectors

  lazy val route = path("mine" ~ Slash.?) {
    post {
      requestEntityPresent {
        entity(as[NodeSeq])(sendPmml)
      }
    }
  } ~ path("partial-result" / JavaUUID ~ Slash.?) {
    id =>
      get {
        receivePartialResult(id)
      }
  } ~ path("complete-result" / JavaUUID ~ Slash.?) {
    id =>
      get {
        receiveCompleteResult(id)
      }
  }

  private def startMining(minerActor: ActorRef, pmml: NodeSeq): Future[MinerResult] = Future {
    logger.info(s"${minerActor.path.name}: mining start...")
    def useMiner(minerTask: MinerTask)(f: Miner => MinerResult) = minerTask.datasetDetail.`type` match {
      case LimitedDatasetType => RScript evalTx { rscript =>
        f(new AprioriMiner(rscript) with MinerTaskValidatorImpl)
      }
      case UnlimitedDatasetType => ???
    }
    logger.trace(s"${minerActor.path.name}: input task is:\n$pmml")
    pmml.find(_.label == "PMML").map(new PmmlTaskParser(_).parse).map(minerTask => useMiner(minerTask) { miner =>
      miner.mine(minerTask) { minerResult =>
        minerActor ! MinerActor.Request.PostPartialResult(minerResult)
      }
    }).getOrElse(throw new ValidationException("Any PMML node has not been found within the input XML file."))
  }

  private def sendPmml(pmml: NodeSeq) = {
    val id = UUID.randomUUID
    val minerActor = actorContext.actorOf(MinerActor.props(id), id.toString)
    val miningProcess = startMining(minerActor, pmml)
    miningProcess.onFailure {
      case th => minerActor ! MinerActor.Request.PostError(th)
    }
    miningProcess.onSuccess {
      case minerResult => minerActor ! MinerActor.Request.PostResult(minerResult)
    }
    requestUri { uri =>
      val rurl = uri.withPath(uri.path.parent / "partial-result" / id.toString)
      respondWithHeader(HttpHeaders.Location(rurl)) {
        complete(
          StatusCodes.Accepted,
          <status>
            <code>202 Accepted</code>
            <miner>
              <state>In progress</state>
              <task-id>{id}</task-id>
              <started>{new Date}</started>
              <partial-result-url>{rurl}</partial-result-url>
            </miner>
          </status>
        )
      }
    }
  }

  private def receivePartialResult(id: UUID) = actorContext.child(id.toString) match {
    case Some(minerActor) =>
      onComplete(minerActor ? MinerActor.Request.GetResult(true)) {
        case Success(MinerActor.Response.PartialResult(result)) => complete(StatusCodes.PartialContent, result: PmmlResult)
        case Success(MinerActor.Response.InProgress) => complete(StatusCodes.NoContent, HttpEntity(ContentType(`application/xml`, HttpCharsets.`UTF-8`), ""))
        case Success(_: MinerActor.Response.Result) => requestUri { uri =>
          val rurl = uri.withPath(uri.path.parent.parent / "complete-result" / id.toString)
          complete(
            StatusCodes.SeeOther,
            List(HttpHeaders.Location(rurl)),
            <status>
              <code>303 See Other</code>
              <miner>
                <state>Done</state>
                <task-id>{id}</task-id>
                <complete-result-url>{rurl}</complete-result-url>
              </miner>
            </status>
          )
        }
        case Failure(UnexpectedActorRequest) => reject
        case Failure(th) => throw th
        case _ => throw UnexpectedActorResponse
      }
    case None => reject
  }

  private def receiveCompleteResult(id: UUID) = actorContext.child(id.toString) match {
    case Some(minerActor) =>
      onComplete(minerActor ? MinerActor.Request.GetResult(false)) {
        case Success(MinerActor.Response.Result(result)) => complete(result: PmmlResult)
        case Failure(UnexpectedActorRequest) => reject
        case Failure(th) => throw th
        case _ => throw UnexpectedActorResponse
      }
    case None => reject
  }

}
