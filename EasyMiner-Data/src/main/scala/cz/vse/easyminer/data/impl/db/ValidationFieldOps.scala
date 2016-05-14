package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.Validators.FieldValidators

/**
 * Created by propan on 23. 8. 2015.
 */
class ValidationFieldOps(ops: FieldOps) extends FieldOps with FieldValidators {

  val dataSource: DataSourceDetail = ops.dataSource

  def renameField(fieldId: Int, newName: String): Unit = {
    Validator(Field(newName, NominalFieldType))
    ops.renameField(fieldId, newName)
  }

  def getField(fieldId: Int): Option[FieldDetail] = ops.getField(fieldId)

  def deleteField(fieldId: Int): Unit = ops.deleteField(fieldId)

  def getAllFields: List[FieldDetail] = ops.getAllFields

}
