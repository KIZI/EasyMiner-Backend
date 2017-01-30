package cz.vse.easyminer.data.impl.parser

import java.io.InputStream

import cz.vse.easyminer.core.util.BasicFunction
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.parser.RdfInputParser.Settings
import org.apache.jena.riot.{Lang, RDFDataMgr}

import scala.collection.JavaConverters._

/**
  * Created by propan on 27. 12. 2016.
  */
class RdfInputParser private(val dataSourceBuilder: DataSourceBuilder, val settings: Settings)(implicit dataSourceToRdfDataSource: DataSourceDetail => RdfDataSource) extends InputParser {

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

  def apply(dataSourceBuilder: DataSourceBuilder, settings: Settings)(implicit dataSourceToRdfDataSource: DataSourceDetail => RdfDataSource): RdfInputParser = new RdfInputParser(dataSourceBuilder, settings)

  case class Settings(format: Lang,
                      compression: Option[CompressionType])

}
