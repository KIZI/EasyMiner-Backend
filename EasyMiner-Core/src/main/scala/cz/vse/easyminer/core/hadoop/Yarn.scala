package cz.vse.easyminer
package core.hadoop

import cz.vse.easyminer.core.util.{AnyToDouble, AnyToInt}
import org.apache.hadoop.yarn.api.records.{ApplicationId, YarnApplicationState}
import org.apache.hadoop.yarn.client.api.YarnClient

/**
  * Created by propan on 25. 5. 2016.
  */
object Yarn {

  private lazy val yarn = {
    val yarn = YarnClient.createYarnClient()
    yarn.init(Hadoop.conf)
    yarn.start()
    actorSystem.registerOnTermination(yarn.close())
    yarn
  }

  def applicationId(strId: String) = {
    val AppId = "(?:application|job)_(\\d+)_(\\d+)".r
    strId match {
      case AppId(AnyToDouble(timestamp), AnyToInt(id)) => Some(ApplicationId.newInstance(timestamp.toLong, id))
      case _ => None
    }
  }

  def kill(applicationId: ApplicationId) = yarn.killApplication(applicationId)

  def applicationState(applicationId: ApplicationId) = yarn.getApplicationReport(applicationId).getYarnApplicationState

  def applicationStateIsFinal(appState: YarnApplicationState) = appState match {
    case YarnApplicationState.FINISHED | YarnApplicationState.FAILED | YarnApplicationState.KILLED => true
    case _ => false
  }

  object Implicits {

    import scala.language.implicitConversions

    implicit def applicationIdToApplicationState(applicationId: ApplicationId): YarnApplicationState = applicationState(applicationId)

  }

}
