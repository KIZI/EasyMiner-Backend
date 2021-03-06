/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.data._
import cz.vse.easyminer.core.util.BasicValidators.{GreaterOrEqual, LowerOrEqual}
import cz.vse.easyminer.data.impl.Validators.DataSourceValidators

/**
  * Created by Vaclav Zeman on 23. 8. 2015.
  */

/**
  * This is a decorator for data source operations object which validates all input parameters
  *
  * @param ops data source operations object
  */
class ValidationDataSourceOps(ops: DataSourceOps) extends DataSourceOps with DataSourceValidators {

  def renameDataSource(dataSourceId: Int, newName: String): Unit = {
    Validator(DataSource(newName, LimitedDataSourceType))
    ops.renameDataSource(dataSourceId, newName)
  }

  def deleteDataSource(dataSourceId: Int): Unit = ops.deleteDataSource(dataSourceId)


  def getInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Seq[Instance] = {
    Validator(offset)(GreaterOrEqual(0))
    Validator(limit)(GreaterOrEqual(1))
    Validator(limit)(LowerOrEqual(1000))
    ops.getInstances(dataSourceId, fieldIds, offset, limit)
  }

  def getAllDataSources: List[DataSourceDetail] = ops.getAllDataSources

  def getDataSource(dataSourceId: Int): Option[DataSourceDetail] = ops.getDataSource(dataSourceId)
}
