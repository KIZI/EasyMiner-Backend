package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.impl.db.mysql.MysqlFieldOps
import cz.vse.easyminer.data.{DataSourceOps, FieldDetail, Instances}

/**
 * Created by propan on 9. 12. 2015.
 */
trait DbDataSourceOps extends DataSourceOps {

  implicit private[db] val mysqlDBConnector: MysqlDBConnector

  private[db] def fetchInstances(dataSourceId: Int, fields: Seq[FieldDetail], offset: Int, limit: Int): Instances

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
      Some(fetchInstances(dataSourceId, fields, offset, limit))
    }
  }

}
