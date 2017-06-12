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

/**
  * This is object which control working with input RDF data source
  * Easyminer also supports RDF model as an input data
  * If we upload RDF then we first save all triples into an intermediate table;
  * after that we count all needed statistics and then we resave it into transactional form; then we can delete a temporary table
  */
trait RdfDataSource {

  /**
    * Save all triples into a database
    *
    * @param it triple iterator
    */
  def save(it: Iterator[Triple]): Unit

  /**
    * Fetch all fields from triplestore; it transforms all predicates to fields
    *
    * @return seq of fields
    */
  def fetchFields(): Seq[Field]

  /**
    * Fetch all transactions from triplestore
    * One transaction represents one specific subject and all its predicate-object pairs
    *
    * @param fields created field detail sequence
    * @return transactions
    */
  def fetchTransactions(fields: Seq[FieldDetail]): Traversable[Set[(FieldDetail, Value)]]

  /**
    * Remove the intermediate table with all triples from database
    */
  def remove(): Unit

}