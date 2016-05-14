package cz.vse.easyminer.miner

import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.db.hive.HiveDataSourceBuilder
import cz.vse.easyminer.data.impl.db.mysql.MysqlDataSourceBuilder
import cz.vse.easyminer.data.impl.parser.CsvInputParser
import cz.vse.easyminer.data.impl.parser.CsvInputParser.Settings

/**
 * Created by propan on 29. 12. 2015.
 */
trait DataDbOps {

  implicit val taskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor

  val defaultCsvSettings = Settings(
    ',',
    Charset.forName("UTF-8"),
    '"',
    '"',
    new Locale("cs"),
    None,
    Set("null", ""),
    List(None, Some(NumericFieldType), None, Some(NominalFieldType), None, None, None, Some(NominalFieldType))
  )

  protected[this] def createBuilder(dataSourceName: String): DataSourceBuilder

  def buildDatasource(dataSourceName: String, data: InputStream, dataTypes: List[Option[FieldType]] = Nil) = {
    val csvInputParser = new CsvInputParser(createBuilder(dataSourceName), if (dataTypes.isEmpty) defaultCsvSettings else defaultCsvSettings.copy(dataTypes = dataTypes))
    csvInputParser.write(data)
  }

}

trait MysqlDataDbOps extends DataDbOps {

  implicit val mysqlDBConnector: MysqlDBConnector

  protected[this] def createBuilder(dataSourceName: String): DataSourceBuilder = MysqlDataSourceBuilder(dataSourceName)

}

trait HiveDataDbOps extends DataDbOps {

  implicit val mysqlDBConnector: MysqlDBConnector
  implicit val hiveDBConnector: HiveDBConnector

  protected[this] def createBuilder(dataSourceName: String): DataSourceBuilder = HiveDataSourceBuilder(dataSourceName)

}