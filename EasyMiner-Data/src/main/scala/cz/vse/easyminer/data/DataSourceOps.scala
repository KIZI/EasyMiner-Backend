package cz.vse.easyminer.data

/**
 * Created by propan on 4. 8. 2015.
 */
trait DataSourceOps {

  def renameDataSource(dataSourceId: Int, newName: String): Unit

  def getDataSource(dataSourceId: Int): Option[DataSourceDetail]

  def deleteDataSource(dataSourceId: Int): Unit

  def getAllDataSources: List[DataSourceDetail]

  /**
   *
   * @param dataSourceId data source id
   * @param fieldIds list of projected fields, if empty it should return all columns
   * @param offset start pointer. First record is 0 (not 1)
   * @param limit number of instaces. It is restricted by the maximal limit value
   * @return instances with fields
   */
  def getInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Option[Instances]

}
