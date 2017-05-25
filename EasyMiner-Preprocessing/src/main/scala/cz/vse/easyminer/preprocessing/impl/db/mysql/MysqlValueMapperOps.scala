/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.ValueTable
import cz.vse.easyminer.preprocessing.{ValueDetail, DatasetDetail}
import cz.vse.easyminer.preprocessing.impl.db.DbValueMapperOps
import scalikejdbc.DBSession

/**
 * Created by Vaclav Zeman on 15. 2. 2016.
 */
class MysqlValueMapperOps private(val dataset: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector) extends DbValueMapperOps {

  import mysqlDBConnector._

  protected[this] val valueDetailTable: scalikejdbc.SQLSyntaxSupport[ValueDetail] = new ValueTable(dataset.id)

  protected[this] def useDbSession[T](f: (DBSession) => T): T = DBConn readOnly f

}

object MysqlValueMapperOps {

  def apply(dataset: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector) = new MysqlValueMapperOps(dataset)

}