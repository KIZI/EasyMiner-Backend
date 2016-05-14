package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 21. 12. 2015.
 */
trait SchemaOps {

  def schemaExists: Boolean

  def createSchema(): Unit

}
