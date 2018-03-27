package cz.vse.easyminer.miner

/**
  * Created by propan on 28. 2. 2017.
  */
trait OutlierDetection {

  val datasetId: Int

  def removeTask(id: Int)

  def getTasks: Seq[OutliersTask]

  def getTask(id: Int): Option[OutliersTask]

  def searchOutliers(support: Double): OutliersTask

  def retrieveOutliers(id: Int, offset: Int, limit: Int): Seq[OutlierWithInstance]

}