package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.impl.db.mysql.MysqlFieldOps
import cz.vse.easyminer.data._

/**
 * Created by propan on 9. 12. 2015.
 */
trait DbDataSourceOps extends DataSourceOps {

  implicit private[db] val mysqlDBConnector: MysqlDBConnector

  private[db] def fetchInstances(dataSource: DataSourceDetail, fields: Seq[FieldDetail], offset: Int, limit: Int): Seq[NarrowInstance]

  def getInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Option[Instances] = getDataSource(dataSourceId).flatMap { dataSource =>
    val fieldOps = new MysqlFieldOps(dataSource)
    val fields = {
      val allFields = fieldOps.getAllFields
      if (fieldIds.isEmpty) {
        allFields
      } else {
        fieldIds.map(id => allFields.find(_.id == id)).collect {
          case Some(x) => x
        }
      }
    }
    if (fields.isEmpty) {
      None
    } else {
      val values = fetchInstances(dataSource, fields, offset, limit).groupBy(_.id)
      val instances = for (i <- (math.max(offset, 0) + 1) to math.min(offset + limit, dataSource.size)) yield {
        Instance(
          i,
          values.get(i).map { valueDetails =>
            val valueDetailMap = valueDetails.map(valueDetail => valueDetail.field -> valueDetail).toMap
            fields.map { fieldDetail =>
              valueDetailMap.get(fieldDetail.id).map(_.value).getOrElse(NullValue)
            }
          }.getOrElse(Seq.fill(fields.size)(NullValue))
        )
      }
      Some(Instances(fields, instances))
    }
  }

}
