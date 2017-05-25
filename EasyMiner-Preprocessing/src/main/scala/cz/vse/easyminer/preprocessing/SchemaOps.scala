/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

/**
 * Created by Vaclav Zeman on 21. 12. 2015.
 */
trait SchemaOps {

  def schemaExists: Boolean

  def createSchema(): Unit

}
