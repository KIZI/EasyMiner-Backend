/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.DataSourceType.DataSourceTypeOps
import cz.vse.easyminer.data._

/**
  * Created by Vaclav Zeman on 16. 2. 2016.
  */

/**
  * Implementation of data source type operations on mysql database.
  * It contains methods, from which we can access to other data source operations.
  *
  * @param mysqlDBConnector    implicit! mysql db connector
  * @param taskStatusProcessor implicit! task processor for monitoring
  */
class MysqlDataSourceTypeOps private[db](implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) extends DataSourceTypeOps[LimitedDataSourceType.type] {


  /**
    * This method creates builder for creation of a new data source and populating it with data
    *
    * @param name new data source name
    * @return data source builder
    */
  def toDataSourceBuilder(name: String): DataSourceBuilder = MysqlDataSourceBuilder(name)


  /**
    * Get operations for data sources
    *
    * @return data sources operations object
    */
  def toDataSourceOps: DataSourceOps = MysqlDataSourceOps()


  /**
    * Get field operations for a data source
    *
    * @param dataSourceDetail data source
    * @return field operations object
    */
  def toFieldOps(dataSourceDetail: DataSourceDetail): FieldOps = MysqlFieldOps(dataSourceDetail)

  /**
    * Get value operations for a field of a data source
    *
    * @param dataSourceDetail data source
    * @param fieldDetail      field
    * @return value operations object
    */
  def toValueOps(dataSourceDetail: DataSourceDetail, fieldDetail: FieldDetail): ValueOps = MysqlValueOps(dataSourceDetail, fieldDetail)

}