package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.util.BasicValidators.{Greater, GreaterOrEqual, LowerOrEqual}
import cz.vse.easyminer.miner.{OutlierDetection, OutlierWithInstance, OutliersTask}

/**
  * Created by propan on 6. 3. 2017.
  */
class ValidationOutlierDetection(outlierDetection: OutlierDetection) extends OutlierDetection {

  val datasetId: Int = outlierDetection.datasetId

  def searchOutliers(support: Double): OutliersTask = {
    Validator(support)(Greater(0.0))
    Validator(support)(LowerOrEqual(1.0))
    outlierDetection.searchOutliers(support)
  }

  def retrieveOutliers(id: Int, offset: Int, limit: Int): Seq[OutlierWithInstance] = {
    Validator(offset)(GreaterOrEqual(0))
    Validator(limit)(Greater(0))
    Validator(limit)(LowerOrEqual(1000))
    outlierDetection.retrieveOutliers(id, offset, limit)
  }

  def removeTask(id: Int): Unit = outlierDetection.removeTask(id)

  def getTasks: Seq[OutliersTask] = outlierDetection.getTasks

  def getTask(id: Int): Option[OutliersTask] = outlierDetection.getTask(id)

}
