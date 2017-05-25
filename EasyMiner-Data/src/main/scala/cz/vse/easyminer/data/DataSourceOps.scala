/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 4. 8. 2015.
  */
trait DataSourceOps {

  def renameDataSource(dataSourceId: Int, newName: String): Unit

  def getDataSource(dataSourceId: Int): Option[DataSourceDetail]

  def deleteDataSource(dataSourceId: Int): Unit

  def getAllDataSources: List[DataSourceDetail]

  /**
    *
    * @param dataSourceId data source id
    * @param fieldIds     list of projected fields, if empty it should return all columns
    * @param offset       start pointer. First record is 0 (not 1)
    * @param limit        number of instaces. It is restricted by the maximal limit value
    * @return instances with fields
    */
  def getInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Seq[Instance]

  final def getAggregatedInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Seq[AggregatedInstance] = getDataSource(dataSourceId).toList.flatMap { dataSourceDetail =>
    val instances = getInstances(dataSourceId, fieldIds, offset, limit).groupBy(_.id)
    for (id <- offset until math.min(offset + limit, dataSourceDetail.size)) yield {
      AggregatedInstance(id + 1, instances.getOrElse(id + 1, Nil).map(instance => AggregatedInstanceItem(instance.field, instance.value)))
    }
  }

}
