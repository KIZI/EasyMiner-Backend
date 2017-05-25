/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db.mysql

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.core.util.AnyToDouble
import cz.vse.easyminer.core.{StatusCodeException, TaskStatusProcessor, Validator}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.Validators.ValueValidators
import cz.vse.easyminer.data.impl.db.mysql.MysqlRdfDataSource.Exceptions
import org.apache.jena.graph.{Node_Literal, Node_URI, Triple}
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 27. 12. 2016.
  */
class MysqlRdfDataSource private(dataSourceDetail: DataSourceDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) extends RdfDataSource with ValueValidators {

  import mysqlDBConnector._

  type Item = (FieldDetail, Value)

  private val batchLimit = 100

  private val rdfTable = new SQLSyntaxSupport[Nothing] {
    override def tableName: String = "rdf_" + dataSourceDetail.id
  }

  private def tripleToValueSeq(triple: Triple): Seq[Any] = {
    val subject = NominalValue(triple.getSubject.getURI)
    val predicate = NominalValue(triple.getPredicate.getURI)
    val `object`: Value = triple.getObject match {
      case x: Node_Literal =>
        val literal = x.getLiteral
        literal.getLexicalForm match {
          case original@AnyToDouble(x) => NumericValue(original, x)
          case x => NominalValue(x)
        }
      case x: Node_URI => NominalValue(x.getURI)
      case _ => throw new Exceptions.InvalidObject(triple)
    }
    Validator(subject)
    Validator(predicate)
    Validator(`object`)
    val (valueNominal, valueNumeric) = `object` match {
      case NominalValue(value) => value -> null
      case NumericValue(original, value) => original -> value
      case _ => throw new Exceptions.InvalidObject(triple)
    }
    List(
      subject.value,
      predicate.value,
      valueNominal,
      valueNumeric,
      valueNumeric == null
    )
  }

  def save(it: Iterator[Triple]): Unit = DBConn autoCommit { implicit session =>
    sql"""CREATE TABLE ${rdfTable.table} (
        subject varchar(255) NOT NULL,
        predicate varchar(255) NOT NULL,
        value_nominal varchar(255) NOT NULL,
        value_numeric double DEFAULT NULL,
        is_nominal BOOLEAN NOT NULL,
        KEY subject (subject),
        KEY predicate (predicate)
        ) ENGINE=MYISAM DEFAULT CHARSET=utf8 COLLATE=utf8_bin""".execute().apply()
    val preparedStatement = sqls"INSERT INTO ${rdfTable.table} (subject, predicate, value_nominal, value_numeric, is_nominal) VALUES (?, ?, ?, ?, ?)".value
    var insertedTriples = 0
    for (triples <- it.grouped(batchLimit)) {
      SQL(preparedStatement).batch(triples.map(tripleToValueSeq): _*).apply()
      insertedTriples += triples.length
      taskStatusProcessor.newStatus("RDF data source is now copying into a database... Inserted triples: " + insertedTriples)
    }
  }

  def fetchFields(): Seq[Field] = DBConn autoCommit { implicit session =>
    sql"SELECT predicate, BIT_OR(is_nominal) AS nominal FROM ${rdfTable.table} GROUP BY predicate"
      .map(wrs => Field(wrs.string("predicate"), if (wrs.boolean("nominal")) NominalFieldType else NumericFieldType))
      .list()
      .apply()
  }

  def fetchTransactions(fields: Seq[FieldDetail]): Traversable[Set[Item]] = new Traversable[Set[Item]] {
    def foreach[U](f: (Set[Item]) => U): Unit = DBConn autoCommit { implicit session =>
      val fieldMap = fields.iterator.map(field => field.name -> field).toMap
      def wrsToItem(wrs: WrappedResultSet): Item = (fieldMap(wrs.string("predicate")), if (wrs.boolean("is_nominal")) NominalValue(wrs.string("value_nominal")) else NumericValue(wrs.string("value_nominal"), wrs.double("value_numeric")))
      val (_, itemset) = sql"SELECT * FROM ${rdfTable.table} ORDER BY subject".foldLeft((Option.empty[String], Set.empty[Item])) {
        case ((None, _), wrs) => (Some(wrs.string("subject")), Set(wrsToItem(wrs)))
        case ((Some(lastSubject), itemset), wrs) =>
          val subject = wrs.string("subject")
          if (subject == lastSubject) {
            (Some(subject), itemset + wrsToItem(wrs))
          } else {
            f(itemset)
            (Some(subject), Set(wrsToItem(wrs)))
          }
      }
      f(itemset)
    }
  }

  def remove(): Unit = DBConn autoCommit { implicit session =>
    sql"DROP TABLE IF EXISTS ${rdfTable.table}".execute().apply()
  }

}

object MysqlRdfDataSource {

  def apply(dataSourceDetail: DataSourceDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) = new MysqlRdfDataSource(dataSourceDetail)

  object Exceptions {

    sealed abstract class RdfParserException(msg: String) extends Exception("An error during RDF parsing: " + msg) with StatusCodeException.BadRequest

    class InvalidObject(triple: Triple) extends RdfParserException("Invalid object, expected URI or LITERAL, actual: " + triple.getObject.toString)

  }

}
