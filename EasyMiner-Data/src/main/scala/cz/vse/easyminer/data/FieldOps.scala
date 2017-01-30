package cz.vse.easyminer.data

/**
  * Created by propan on 20. 8. 2015.
  */
trait FieldOps {

  val dataSource: DataSourceDetail

  def renameField(fieldId: Int, newName: String): Unit

  def deleteField(fieldId: Int): Unit

  def changeFieldType(fieldId: Int): Boolean

  //TODO
  //def transform(transformation: Transformation): Unit

  def getAllFields: List[FieldDetail]

  def getField(fieldId: Int): Option[FieldDetail]

}
