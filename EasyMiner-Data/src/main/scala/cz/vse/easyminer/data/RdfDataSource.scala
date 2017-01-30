package cz.vse.easyminer.data

import org.apache.jena.graph.Triple

/**
  * Created by propan on 27. 12. 2016.
  */
trait RdfDataSource {

  def save(it: Iterator[Triple]): Unit

  def fetchFields(): Seq[Field]

  def fetchTransactions(fields: Seq[FieldDetail]): Traversable[Set[(FieldDetail, Value)]]

  def remove(): Unit

}