package cz.vse.easyminer.miner

import cz.vse.easyminer.core.db.{LimitedDBType, MysqlDBConnector}
import cz.vse.easyminer.miner.impl.MysqlMinerDatasetOps
import cz.vse.easyminer.preprocessing.DatasetDetail
import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._
import org.scalatest.{FlatSpec, Matchers}
import scalikejdbc._

class DatasetSpec extends FlatSpec with Matchers with ConfOpt with MysqlMinerDatasetOps {

  import MysqlPreprocessingDbOps._

  val mysqlDBConnector: MysqlDBConnector = DBSpec.dbConnectors.connector(LimitedDBType)
  val datasetDetail: DatasetDetail = datasetBarbora.datasetDetail
  val attributes = datasetDetail.`type`.toAttributeOps(datasetDetail).getAllAttributes

  "MySQLDataset" should "execute all its queries" in {
    val ratingAttribute = attributes.find(_.name == "rating").get
    val ratingValueIds = datasetDetail.`type`.toValueOps(datasetDetail, ratingAttribute).getValues(0, 10).map(_.id)
    fetchValuesBySelectAndAttribute(ratingAttribute, sqls"${valueTable.column.id}") should contain only (ratingValueIds: _*)
    fetchComplementedValuesBySelectsAndAttribute(
      ratingAttribute,
      sqls"${valueTable.column.id}",
      sqls"IF(${valueTable.column.id} = ${ratingValueIds.head}, ${valueTable.column.id}, NULL) AS ${valueTable.column.id}"
    ) should contain only (ratingValueIds.tail: _*)
    fetchIntersectedValuesBySelectsAndAttribute(
      ratingAttribute,
      sqls"${valueTable.column.id}",
      sqls"IF(${valueTable.column.id} = ${ratingValueIds.head}, ${valueTable.column.id}, NULL) AS ${valueTable.column.id}"
    ) should contain only (ratingValueIds.take(1): _*)
  }

}