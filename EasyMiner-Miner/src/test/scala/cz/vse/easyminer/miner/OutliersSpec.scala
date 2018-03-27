package cz.vse.easyminer.miner

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import MysqlPreprocessingDbOps._
import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.data.impl.db.mysql.MysqlDataSourceOps
import cz.vse.easyminer.data.{AggregatedInstance, AggregatedInstanceItem, NominalValue, NumericValue}
import cz.vse.easyminer.miner.impl.mysql.{MysqlOutlierDetection, Tables}
import cz.vse.easyminer.miner.impl.r.FpOutlierDetection
import cz.vse.easyminer.preprocessing.{InstanceItemWithValue, InstanceWithValue}
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlDatasetOps
import org.joda.time.DateTime
import org.rosuda.REngine.REngineException
import scalikejdbc._

/**
  * Created by propan on 6. 3. 2017.
  */
class OutliersSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  import datasetBarbora._

  lazy val fpOutlierDetection = FpOutlierDetection(datasetDetail.id)

  override protected def beforeAll(): Unit = {
    mysqlDBConnector.DBConn autoCommit { implicit session =>
      val tablePattern = Tables.tablePrefix + "outliers%"
      val tables = sql"SHOW TABLES LIKE $tablePattern".map(_.string(1)).list().apply()
      tables.foreach(table => SQL(s"DROP TABLE IF EXISTS $table").execute().apply())
    }
  }

  "FpOutlierDetection" should "create outliers in mysql table" in {
    fpOutlierDetection.getTasks shouldBe empty
    fpOutlierDetection.getTask(0) shouldBe empty
    val outliersTask = fpOutlierDetection.searchOutliers(0.01)
    new DateTime(outliersTask.time).toString("d.M.Y") shouldBe new DateTime().toString("d.M.Y")
  }

  it should "return outliers" in {
    val tasks = fpOutlierDetection.getTasks
    tasks should not be empty
    for (task <- tasks) {
      fpOutlierDetection.getTask(task.id) shouldBe Some(task)
      var outliers = fpOutlierDetection.retrieveOutliers(task.id, 0, 500)
      outliers.size shouldBe 500
      outliers.foreach(_.score should be > 0.0)
      outliers = fpOutlierDetection.retrieveOutliers(task.id, 1000, 1000)
      outliers.size shouldBe 1000
      outliers = fpOutlierDetection.retrieveOutliers(task.id, 0, 2)
      outliers.size shouldBe 2
      outliers should matchPattern { case List(
      OutlierWithInstance(3097.55593607306, InstanceWithValue(2, List(_, InstanceItemWithValue(_, "D")))),
      OutlierWithInstance(2096.07825515827, InstanceWithValue(5073, List(InstanceItemWithValue(_, "65"), InstanceItemWithValue(_, "Opava"), InstanceItemWithValue(_, "B"))))) =>
      }
    }
  }

  it should "throw exception if bad data input" in {
    intercept[ValidationException] {
      fpOutlierDetection.searchOutliers(0)
    }
    intercept[ValidationException] {
      fpOutlierDetection.searchOutliers(2)
    }
    intercept[ValidationException] {
      fpOutlierDetection.getTasks.foreach(task => fpOutlierDetection.retrieveOutliers(task.id, -1, 1))
    }
    intercept[ValidationException] {
      fpOutlierDetection.getTasks.foreach(task => fpOutlierDetection.retrieveOutliers(task.id, 0, 0))
    }
  }

  it should "remove outliers results" in {
    val tasks = fpOutlierDetection.getTasks
    tasks should not be empty
    tasks.foreach(x => fpOutlierDetection.removeTask(x.id))
    fpOutlierDetection.getTasks shouldBe empty
  }

  it should "remove all isolated outliers" in {
    fpOutlierDetection.searchOutliers(0.01)
    fpOutlierDetection.getTasks should not be empty
    MysqlDatasetOps().deleteDataset(datasetDetail.id)
    intercept[REngineException] {
      fpOutlierDetection.searchOutliers(0.01)
    }
    val tasks = fpOutlierDetection.getTasks
    tasks should not be empty
    tasks.foreach(task => fpOutlierDetection.retrieveOutliers(task.id, 0, 10).size shouldBe 0)
    MysqlDataSourceOps().deleteDataSource(datasetDetail.dataSource)
    fpOutlierDetection.getTasks should not be empty
    MysqlOutlierDetection.clearZombie { id =>
      List(FpOutlierDetection(id))
    }
    fpOutlierDetection.getTasks shouldBe empty
  }

}
