/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.Validators.FieldValidators

/**
  * Created by Vaclav Zeman on 23. 8. 2015.
  */

/**
  * This is a decorator for field operations object which validates all input parameters
  *
  * @param ops field operations object
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

  def changeFieldType(fieldId: Int): Boolean = ops.changeFieldType(fieldId)

}
