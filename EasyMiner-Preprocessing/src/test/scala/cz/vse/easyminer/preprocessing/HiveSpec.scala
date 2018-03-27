package cz.vse.easyminer
package preprocessing

import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.core.{HiveUserDatabase, PersistentLock}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.hive
import cz.vse.easyminer.data.impl.db.hive.{HiveDataSourceOps, HiveFieldOps}
import cz.vse.easyminer.preprocessing.impl.PersistentLocks
import cz.vse.easyminer.preprocessing.impl.db.hive._
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlSchemaOps
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * Created by propan on 9. 2. 2016.
  */
class HiveSpec extends FlatSpec with Matchers with BeforeAndAfterAll with HiveDataDbOps {

  implicit val mysqlDBConnector: MysqlDBConnector = DBSpec.makeMysqlConnector(false)

  implicit val hiveDBConnector: HiveDBConnector = HiveSpec.makeHiveConnector

  lazy val dataSourceOps = HiveDataSourceOps()

  override protected def beforeAll(): Unit = {
    val schemaOps = MysqlSchemaOps()
    if (schemaOps.schemaExists) {
      HiveSpec.rollbackData()
      DBSpec.rollbackData()
    }
    schemaOps.createSchema()
    //if (!schemaOps.schemaExists) schemaOps.createSchema()
  }

  "Data service" should "create data source" in {
    val dataSourceDetail = buildDatasource("test", getClass.getResourceAsStream("/test.csv"))
    dataSourceDetail.name shouldBe "test"
    dataSourceDetail.size shouldBe 6181
    val dataSources = dataSourceOps.getAllDataSources
    dataSources should contain only dataSourceDetail
  }

  "Dataset builder" should "create a dataset" in {
    HiveDatasetOps().getAllDatasets.foreach(x => HiveDatasetOps().deleteDataset(x.id))
    for (dataSource <- dataSourceOps.getAllDataSources) {
      val datasetDetail = HiveDatasetBuilder(Dataset("test-prep", dataSource)).build
      datasetDetail.name shouldBe "test-prep"
      datasetDetail.`type` shouldBe UnlimitedDatasetType
      datasetDetail.dataSource shouldBe dataSource.id
      datasetDetail.size shouldBe 6181
    }
  }

  "Simple attribute builder" should "create an attribute from a field" in {
    val attributeList: List[AttributeDetail] = for {
      datasetDetail <- HiveDatasetOps().getAllDatasets
      dataSourceDetail <- dataSourceOps.getDataSource(datasetDetail.dataSource).toList
      fieldDetail <- HiveFieldOps(dataSourceDetail).getAllFields
    } yield {
      val attributeDetail = HiveSimpleAttributeBuilder(List(SimpleAttribute(fieldDetail.name + "-attr", fieldDetail.id, Nil)), datasetDetail).build.head
      attributeDetail.dataset shouldBe datasetDetail.id
      attributeDetail.field shouldBe fieldDetail.id
      attributeDetail.name shouldBe (fieldDetail.name + "-attr")
      attributeDetail.uniqueValuesSize shouldBe fieldDetail.uniqueValuesSize
      attributeDetail
    }
    attributeList.size shouldBe 4
  }

  it should "preprocess a group of simple attributes at once by collective attribute builder" in {
    for {
      datasetDetail <- HiveDatasetOps().getAllDatasets
      dataSourceDetail <- dataSourceOps.getDataSource(datasetDetail.dataSource).toList
    } {
      val fields = HiveFieldOps(dataSourceDetail).getAllFields
      val attributeDetails = HiveSimpleAttributeBuilder(fields.map(x => SimpleAttribute(x.name + "-attr-collective", x.id, Nil)), datasetDetail).build
      attributeDetails.size shouldBe fields.size
      for ((attributeDetail, field) <- attributeDetails.zip(fields)) {
        attributeDetail.uniqueValuesSize shouldBe field.uniqueValuesSize
      }
    }
  }

  it should "deny to build attribute in parallel" in {
    import cz.vse.easyminer.preprocessing.impl.db._

    import scala.concurrent.ExecutionContext.Implicits._
    for {
      datasetDetail <- HiveDatasetOps().getAllDatasets
      dataSourceDetail <- dataSourceOps.getDataSource(datasetDetail.dataSource).toList
      fieldDetail <- HiveFieldOps(dataSourceDetail).getAllFields if fieldDetail.`type` == NumericFieldType
    } {
      val futureAttribute = Future {
        PersistentLock(PersistentLocks.datasetLockName(datasetDetail.id)) {
          HiveSimpleAttributeBuilder(List(SimpleAttribute(fieldDetail.name + "-attr-par1", fieldDetail.id, Nil)), datasetDetail).build.head
        }
      }
      Thread.sleep(1000)
      intercept[PersistentLock.Exceptions.LockedContext] {
        HiveSimpleAttributeBuilder(List(SimpleAttribute(fieldDetail.name + "-attr-par2", fieldDetail.id, Nil)), datasetDetail).build
      }
      Thread.sleep(1000)
      intercept[PersistentLock.Exceptions.LockedContext] {
        HiveDatasetOps().deleteDataset(datasetDetail.id)
      }
      val attributeOps = HiveAttributeOps(datasetDetail)
      for (attributeDetail <- attributeOps.getAllAttributes) {
        intercept[PersistentLock.Exceptions.LockedContext] {
          attributeOps.deleteAttribute(attributeDetail.id)
        }
      }
      val attributeDetail = Await.result(futureAttribute, Duration.Inf)
      attributeDetail.uniqueValuesSize shouldBe fieldDetail.uniqueValuesSize
      attributeOps.deleteAttribute(attributeDetail.id)
    }
  }

  "ValueOps" should "return attribute values" in {
    val performedTestList = for {
      datasetDetail <- HiveDatasetOps().getAllDatasets
      fieldOps = HiveFieldOps(dataSourceOps.getDataSource(datasetDetail.id).get)
      attributeDetail <- HiveAttributeOps(datasetDetail).getAllAttributes
      fieldDetail = fieldOps.getField(attributeDetail.field).get
    } yield {
      val valueOps = HiveValueOps(datasetDetail, attributeDetail)
      val dataValueOps = hive.HiveValueOps(fieldOps.dataSource, fieldDetail)
      if (attributeDetail.uniqueValuesSize == 4) {
        val values = valueOps.getValues(0, 10)
        values.size shouldBe 4
        values.map { x => (x.value, x.frequency) } should contain theSameElementsAs List("A" -> 1827, "B" -> 289, "C" -> 3627, "D" -> 438)
        valueOps.getValues(1, 1).map(_.value) should contain only "B"
        1
      } else {
        valueOps.getValues(0, 1000).size shouldBe dataValueOps.getValues(0, 1000).size
        1
      }
    }
    performedTestList.sum shouldBe 8
  }

  "ValueMapperOps" should "map an original value to an indexed value and vice versa" in {
    def valueDetailToValue(valueDetail: ValueDetail) = NominalValue(valueDetail.value)
    for (datasetDetail <- HiveDatasetOps().getAllDatasets) {
      val valueMapperOps = HiveValueMapperOps(datasetDetail)
      val valueDetails = HiveAttributeOps(datasetDetail).getAllAttributes.map(attributeDetail => attributeDetail -> HiveValueOps(datasetDetail, attributeDetail).getValues(0, 1000))
      val valueMapper = valueMapperOps.valueMapper(valueDetails.map(x => x._1 -> x._2.map(valueDetailToValue).toSet[NominalValue]).toMap)
      val normalizedValueMapper = valueMapperOps.itemMapper(valueDetails.map(x => x._1 -> x._2.map(x => x.id).toSet).toMap)
      for {
        (attributeDetail, values) <- valueDetails
        valueDetail <- values
      } {
        valueMapper.item(attributeDetail, valueDetailToValue(valueDetail)) shouldBe Some(valueDetail.id)
        normalizedValueMapper.value(attributeDetail, valueDetail.id) shouldBe Some(valueDetailToValue(valueDetail))
        valueMapper.item(attributeDetail, NominalValue("nothing")) shouldBe None
        normalizedValueMapper.value(attributeDetail, 0) shouldBe None
      }
    }
  }

  "AttributeOps" should "perform all methods" in {
    val performedTestList = for (datasetDetail <- HiveDatasetOps().getAllDatasets) yield {
      val attributeOps = HiveAttributeOps(datasetDetail)
      attributeOps.getAllAttributes.size shouldBe 8
      for (attributeDetail <- attributeOps.getAllAttributes) {
        Some(attributeDetail) shouldBe attributeOps.getAttribute(attributeDetail.id)
        attributeOps.renameAttribute(attributeDetail.id, "new-name")
        attributeOps.getAttribute(attributeDetail.id).map(_.name) shouldBe Some("new-name")
        attributeOps.renameAttribute(attributeDetail.id, attributeDetail.name)
        if (attributeDetail.name == "district-attr") {
          attributeOps.deleteAttribute(attributeDetail.id)
          attributeOps.getAllAttributes.size shouldBe 7
        }
      }
      1
    }
    performedTestList.sum shouldBe 1
  }

  "DatasetOps" should "perform all methods" in {
    val datasetOps = HiveDatasetOps()
    datasetOps.getAllDatasets.size shouldBe 1
    for (datasetDetail <- datasetOps.getAllDatasets) yield {
      Some(datasetDetail) shouldBe datasetOps.getDataset(datasetDetail.id)
      datasetOps.renameDataset(datasetDetail.id, "new-name")
      datasetOps.getDataset(datasetDetail.id).map(_.name) shouldBe Some("new-name")
      datasetOps.deleteDataset(datasetDetail.id)
    }
    datasetOps.getAllDatasets.size shouldBe 0
  }

  override protected def afterAll(): Unit = {
    //HiveSpec.rollbackData()
    //DBSpec.rollbackData()
    hiveDBConnector.close()
    mysqlDBConnector.close()
  }

}

object HiveSpec extends ConfOpt {

  def makeHiveConnector = new HiveDBConnector(HiveUserDatabase(hiveserver, hiveport, hivename, hiveuser))

  def rollbackData()(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector) = {
    val datasetOps = HiveDatasetOps()
    for (dataset <- datasetOps.getAllDatasets) {
      datasetOps.deleteDataset(dataset.id)
    }
    val dataSourceOps = HiveDataSourceOps()
    for (dataSource <- dataSourceOps.getAllDataSources) {
      dataSourceOps.deleteDataSource(dataSource.id)
    }
  }

  def apply(f: (MysqlDBConnector, HiveDBConnector) => Unit): Unit = {
    implicit val hiveConnector = makeHiveConnector
    DBSpec { implicit mysqlConnector =>
      try {
        f(mysqlConnector, hiveConnector)
      } finally {
        rollbackData()
        hiveConnector.close()
      }
    }
  }

}