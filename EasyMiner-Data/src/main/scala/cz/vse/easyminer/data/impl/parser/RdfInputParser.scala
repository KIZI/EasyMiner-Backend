/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.parser

import java.io.InputStream

import cz.vse.easyminer.core.util.BasicFunction
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.parser.RdfInputParser.Settings
import org.apache.jena.riot.{Lang, RDFDataMgr}

import scala.collection.JavaConverters._

/**
  * Created by Vaclav Zeman on 27. 12. 2016.
  */

/**
  * This parses RDF input, saves triples into an intermediate table and then resaves all into transactional database
  *
  * @param dataSourceBuilder         data source builder for creation of transactional data from RDF input
  * @param settings                  RDF input settings
  * @param dataSourceToRdfDataSource implicit! function for creation of RDF data source from data source detail
  */
class RdfInputParser private(val dataSourceBuilder: DataSourceBuilder, val settings: Settings)(implicit dataSourceToRdfDataSource: DataSourceDetail => RdfDataSource) extends InputParser {

  /**
    * Create data source detail from RDF input stream.
    * This reads lines of input then saves all triples into temporary table.
    * From temporary table we create transactions and build a data source.
    *
    * @param is input stream
    * @return created data source detail
    */
  def write(is: InputStream): DataSourceDetail = {
    BasicFunction.tryClose(new LineBoundedInputStream(settings.compression.map(CompressionType.decompressInputStream(is)).getOrElse(is), 1 * 1000 * 1000)) { is =>
      val it = RDFDataMgr.createIteratorTriples(is, settings.format, null).asScala
      dataSourceBuilder.build { fieldBuilder =>
        val rds = dataSourceToRdfDataSource(fieldBuilder.dataSource)
        try {
          rds.save(it)
          rds.fetchFields().foldLeft(fieldBuilder)(_.field(_)).build { valueBuilder =>
            rds.fetchTransactions(valueBuilder.fields).foldLeft(valueBuilder)(_.addTransaction(_)).build
          }
        } finally {
          rds.remove()
        }
      }
    }
  }

}

object RdfInputParser {

  /**
    * This parses RDF input, saves triples into an intermediate table and then resaves all into transactional database
    *
    * @param dataSourceBuilder         data source builder for creation of transactional data from RDF input
    * @param settings                  RDF input settings
    * @param dataSourceToRdfDataSource implicit! function for creation of RDF data source from data source detail
    * @return rdf input parser
    */
  def apply(dataSourceBuilder: DataSourceBuilder, settings: Settings)(implicit dataSourceToRdfDataSource: DataSourceDetail => RdfDataSource): RdfInputParser = new RdfInputParser(dataSourceBuilder, settings)

  case class Settings(format: Lang,
                      compression: Option[CompressionType])

}
