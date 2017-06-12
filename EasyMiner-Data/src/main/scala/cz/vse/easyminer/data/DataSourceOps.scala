/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 4. 8. 2015.
  */

/**
  * All operations for an existed data source
  */
trait DataSourceOps {

  /**
    * Rename data source
    *
    * @param dataSourceId data source id
    * @param newName      new name
    */
  def renameDataSource(dataSourceId: Int, newName: String): Unit

  /**
    * Get detail of a data source
    *
    * @param dataSourceId data source id
    * @return data source detail or None if there is no data source with the ID
    */
  def getDataSource(dataSourceId: Int): Option[DataSourceDetail]

  /**
    * Delete data source
    *
    * @param dataSourceId data source id
    */
  def deleteDataSource(dataSourceId: Int): Unit

  /**
    * Get all details of all existed data sources
    *
    * @return list of data sources
    */
  def getAllDataSources: List[DataSourceDetail]

  /**
    * Get instances (transactions) with form of couples for a data source
    *
    * @param dataSourceId data source id
    * @param fieldIds     list of projected fields, if empty it should return all columns
    * @param offset       start pointer. First record is 0 (not 1)
    * @param limit        number of couples. It is restricted by the maximal limit value
    * @return instances with fields
    */
  def getInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Seq[Instance]

  /**
    * Same as getInstances method, but couples are aggregated by a transaction ID
    *
    * @param dataSourceId data source id
    * @param fieldIds     list of projected fields, if empty it should return all columns
    * @param offset       start pointer. First record is 0 (not 1)
    * @param limit        number of couples. It is restricted by the maximal limit value
    * @return aggregated instances; result is seq of transactions
    */
  final def getAggregatedInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Seq[AggregatedInstance] = getDataSource(dataSourceId).toList.flatMap { dataSourceDetail =>
    val instances = getInstances(dataSourceId, fieldIds, offset, limit).groupBy(_.id)
    for (id <- offset until math.min(offset + limit, dataSourceDetail.size)) yield {
      AggregatedInstance(id + 1, instances.getOrElse(id + 1, Nil).map(instance => AggregatedInstanceItem(instance.field, instance.value)))
    }
  }

}
