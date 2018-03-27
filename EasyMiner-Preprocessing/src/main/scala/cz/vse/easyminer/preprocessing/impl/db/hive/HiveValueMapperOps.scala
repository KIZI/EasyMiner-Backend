package cz.vse.easyminer.preprocessing.impl.db.hive

import cz.vse.easyminer.core.db.HiveDBConnector
import cz.vse.easyminer.preprocessing.impl.db.DbValueMapperOps
import cz.vse.easyminer.preprocessing.impl.db.hive.Tables.ValueTable
import cz.vse.easyminer.preprocessing.{DatasetDetail, ValueDetail}
import scalikejdbc.DBSession

/**
 * Created by propan on 15. 2. 2016.
 */
class HiveValueMapperOps private(val dataset: DatasetDetail)(implicit hiveDBConnector: HiveDBConnector) extends DbValueMapperOps {

  import hiveDBConnector._

  protected val valueDetailTable: scalikejdbc.SQLSyntaxSupport[ValueDetail] = new ValueTable(dataset.id)

  protected def useDbSession[T](f: (DBSession) => T): T = DBConn readOnly f

}

object HiveValueMapperOps {

  def apply(dataset: DatasetDetail)(implicit hiveDBConnector: HiveDBConnector) = new HiveValueMapperOps(dataset)

}