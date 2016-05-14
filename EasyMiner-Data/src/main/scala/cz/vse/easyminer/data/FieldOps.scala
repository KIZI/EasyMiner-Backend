package cz.vse.easyminer.data

/**
 * Created by propan on 20. 8. 2015.
 */
trait FieldOps {

  val dataSource: DataSourceDetail

  def renameField(fieldId: Int, newName: String): Unit

  def deleteField(fieldId: Int): Unit

  def getAllFields: List[FieldDetail]

  def getField(fieldId: Int): Option[FieldDetail]

}
