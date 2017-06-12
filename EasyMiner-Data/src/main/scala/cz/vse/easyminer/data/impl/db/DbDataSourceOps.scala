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

/**
  * Abstraction for data source operations; we work only with mysql db connector because we deal only with meta data
  */
trait DbDataSourceOps extends DataSourceOps {

  implicit private[db] val mysqlDBConnector: MysqlDBConnector

}
