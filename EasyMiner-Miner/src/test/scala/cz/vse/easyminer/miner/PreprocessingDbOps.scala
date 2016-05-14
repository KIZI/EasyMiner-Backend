package cz.vse.easyminer.miner

import java.io.InputStream

import cz.vse.easyminer.core.db._
import cz.vse.easyminer.data.DataSourceType.DataSourceTypeOps
import cz.vse.easyminer.data._
import cz.vse.easyminer.preprocessing.{ValueDetail => PreprocessingValueDetail, NominalValueDetail => PreprocessingNominalValueDetail, NumericValueDetail => PreprocessingNumericValueDetail, _}

import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing.impl.db.hive.Tables.{InstanceTable => HiveInstanceTable}
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.{InstanceTable => MysqlInstanceTable}

/**
 * Created by propan on 29. 12. 2015.
 */

trait PreprocessingDbOps {

  self: DataDbOps =>

  protected[this] val dataSourceTypeOps: DataSourceTypeOps[_]

  protected[this] val datasetTypeOps: DatasetTypeOps[_]

  def buildDataset(dataSourceName: String, data: InputStream, dataTypes: List[Option[FieldType]] = Nil): DatasetDetails = new DatasetDetails(
    datasetTypeOps.toDatasetOps.getAllDatasets.find(x => x.name == dataSourceName && dataSourceTypeOps.toDataSourceOps.getDataSource(x.dataSource).nonEmpty).getOrElse {
      val dataSourceDetail = buildDatasource(dataSourceName, data, dataTypes)
      val datasetDetail = datasetTypeOps.toDatasetBuilder(Dataset(dataSourceDetail.name, dataSourceDetail)).build
      val attributes = dataSourceTypeOps.toFieldOps(dataSourceDetail).getAllFields.map(field => SimpleAttribute(field.name, field.id))
      datasetTypeOps.toCollectiveAttributeBuilder(datasetDetail, attributes: _*).build
      datasetDetail
    }
  )

  class DatasetDetails private[PreprocessingDbOps](val datasetDetail: DatasetDetail) {

    val attributes = datasetTypeOps.toAttributeOps(datasetDetail).getAllAttributes
    val instanceTable = datasetDetail.`type` match {
      case LimitedDatasetType => new MysqlInstanceTable(datasetDetail.id, attributes.map(_.id))
      case UnlimitedDatasetType => new HiveInstanceTable(datasetDetail.id, attributes.map(_.id))
    }
    val attributeMap = attributes.map(x => x.name -> x).toMap
    val valueMap: Map[String, Map[String, PreprocessingValueDetail]] = attributeMap.map { case (key, attributeDetail) =>
      key -> datasetTypeOps.toValueOps(datasetDetail, attributeDetail).getValues(0, 1000).collect {
        case x: PreprocessingNominalValueDetail => x.value -> x
        case x: PreprocessingNumericValueDetail => x.value.toInt.toString -> x
      }.toMap
    }

    def colName(attributeName: String) = instanceTable.columnById(attributeMap(attributeName).id).value

    def valueId(attributeName: String, value: String) = valueMap(attributeName)(value).id

  }

}

trait MysqlPreprocessingDbOps extends PreprocessingDbOps {

  self: MysqlDataDbOps =>

  import cz.vse.easyminer.data.impl.db.DataSourceTypeConversions._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._

  implicit val dbConnectors: DBConnectors
  implicit val mysqlDBConnector: MysqlDBConnector = dbConnectors.connector(LimitedDBType)

  protected[this] lazy val datasetTypeOps: DatasetTypeOps[_] = limitedDatasetTypeToMysqlDatasetTypeOps(LimitedDatasetType)
  protected[this] lazy val dataSourceTypeOps: DataSourceTypeOps[_] = LimitedDataSourceType

}

object MysqlPreprocessingDbOps extends MysqlPreprocessingDbOps with MysqlDataDbOps {

  implicit lazy val dbConnectors: DBConnectors = DBSpec.dbConnectors

  lazy val datasetBarbora = buildDataset("barbora-limited", getClass.getResourceAsStream("/barbora.csv"))

  lazy val datasetAudiology = buildDataset("audiology-limited", getClass.getResourceAsStream("/audiology.csv"), List.fill(70)(Some(NominalFieldType)))

}

trait HivePreprocessingDbOps extends PreprocessingDbOps {

  self: HiveDataDbOps =>

  import cz.vse.easyminer.data.impl.db.DataSourceTypeConversions._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._

  implicit val dbConnectors: DBConnectors
  implicit val mysqlDBConnector: MysqlDBConnector = dbConnectors.connector(LimitedDBType)
  implicit val hiveDBConnector: HiveDBConnector = dbConnectors.connector(UnlimitedDBType)

  protected[this] lazy val dataSourceTypeOps: DataSourceTypeOps[_] = UnlimitedDataSourceType
  protected[this] lazy val datasetTypeOps: DatasetTypeOps[_] = UnlimitedDatasetType

}

object HivePreprocessingDbOps extends HivePreprocessingDbOps with HiveDataDbOps {

  implicit lazy val dbConnectors: DBConnectors = DBSpec.dbConnectors

  lazy val datasetBarbora = buildDataset("barbora-unlimited", getClass.getResourceAsStream("/barbora.csv"))

}