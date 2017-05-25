/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._

/**
  * Created by Vaclav Zeman on 9. 12. 2015.
  */
trait DbDataSourceOps extends DataSourceOps {

  implicit private[db] val mysqlDBConnector: MysqlDBConnector

}
