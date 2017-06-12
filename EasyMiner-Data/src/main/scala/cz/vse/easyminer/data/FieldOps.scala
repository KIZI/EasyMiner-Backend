/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 20. 8. 2015.
  */

/**
  * All operations for existed data fields within a data source
  */
trait FieldOps {

  /**
    * data source detail
    */
  val dataSource: DataSourceDetail

  /**
    * Rename field name
    *
    * @param fieldId field id
    * @param newName new name
    */
  def renameField(fieldId: Int, newName: String): Unit

  /**
    * Delete field from the data source
    *
    * @param fieldId field id
    */
  def deleteField(fieldId: Int): Unit

  /**
    * Change type of a field (nominal -> numeric, numeric -> nominal)
    *
    * @param fieldId field id
    * @return the change was successful
    */
  def changeFieldType(fieldId: Int): Boolean

  //TODO
  //def transform(transformation: Transformation): Unit

  /**
    * Get all fields within the data source
    *
    * @return list of fields
    */
  def getAllFields: List[FieldDetail]

  /**
    * Get field detail by field id
    *
    * @param fieldId field id
    * @return field detail or None if there is no data field with the ID
    */
  def getField(fieldId: Int): Option[FieldDetail]

}
