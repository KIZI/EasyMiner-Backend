/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

import org.apache.jena.graph.Triple

/**
  * Created by Vaclav Zeman on 27. 12. 2016.
  */
trait RdfDataSource {

  def save(it: Iterator[Triple]): Unit

  def fetchFields(): Seq[Field]

  def fetchTransactions(fields: Seq[FieldDetail]): Traversable[Set[(FieldDetail, Value)]]

  def remove(): Unit

}