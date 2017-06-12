/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.parser

import java.io.InputStream
import java.nio.charset.Charset
import java.text.{NumberFormat, ParseException}
import java.util.Locale

import cz.vse.easyminer.core._
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.Validators
import cz.vse.easyminer.data.impl.parser.CsvInputParser.Exceptions._
import cz.vse.easyminer.data.impl.parser.CsvInputParser.Settings

import scala.annotation.tailrec
import scala.io.Source

/**
  * Created by Vaclav Zeman on 27. 7. 2015.
  */

/**
  * This class parses CSV input stream and sends parsed chunks to a data source builder.
  * Data are reading in stream, so this solution does not consume too much memory and is able to parse very large (infinite) CSV files.
  *
  * @param dataSourceBuilder this parser creates data source from an input stream and uses a specific data source builder
  * @param settings          settings of input CSV format
  */
class CsvInputParser(val dataSourceBuilder: DataSourceBuilder, val settings: Settings) extends InputParser {

  /**
    * number format for a specific localization
    */
  implicit private val numberFormat = NumberFormat.getNumberInstance(settings.locale)

  sealed trait DataBuilderCsvAdapter {

    def writeValue(value: String, fieldType: FieldType): DataBuilderCsvAdapter

  }

  case class FieldDataBuilderCsvAdapter(builder: FieldBuilder) extends DataBuilderCsvAdapter {

    def writeValue(value: String, fieldType: FieldType): DataBuilderCsvAdapter = {
      val normField = value.trim
      if (normField.isEmpty) {
        throw EmptyColName
      }
      val field = Field(normField, fieldType)
      this.copy(builder = builder.field(field))
    }

  }

  case class ValueDataBuilderCsvAdapter(buffer: Vector[Value] = Vector()) extends DataBuilderCsvAdapter {

    def writeValue(value: String, fieldType: FieldType): DataBuilderCsvAdapter = {
      val normValue = value.trim
      val typedValue = if (settings.nullValues(normValue)) {
        //it is null value
        NullValue
      } else {
        try {
          //numeric value - parse it as double and save
          NumericValue(normValue)
        } catch {
          //nominal value - save it as nominal
          case _: ParseException => NominalValue(normValue)
        }
      }
      this.copy(buffer = buffer :+ typedValue)
    }

  }

  type FieldTypeList = List[Option[FieldType]]

  private def processRow(lines: Iterator[String], builder: DataBuilderCsvAdapter): DataBuilderCsvAdapter = {
    @tailrec
    def processMultiLine(history: String = "")(implicit fieldTypes: FieldTypeList, builder: DataBuilderCsvAdapter): DataBuilderCsvAdapter = if (lines.hasNext) {
      //trimmed line
      val line = lines.next().trim
      if (line.isEmpty && history.isEmpty) {
        //next line
        processMultiLine()
      } else {
        //process line, if the history buffer is not empty it connects to the column of the previous line (go to quotes environment)
        val (newHistory, newFieldTypes, newBuilder) = processLine(line.toList, history, history.nonEmpty)
        if (newHistory.nonEmpty) {
          processMultiLine(newHistory)(newFieldTypes, newBuilder)
        } else {
          newBuilder
        }
      }
    } else {
      builder
    }
    @tailrec
    def processLine(line: List[Char], buffer: String, innerQuotes: Boolean)(implicit fieldTypes: FieldTypeList, builder: DataBuilderCsvAdapter): (String, FieldTypeList, DataBuilderCsvAdapter) = {
      //if the column size is too large throw exception
      if (buffer.length > CsvInputParser.maxColumnSize && buffer.trim.length > CsvInputParser.maxColumnSize) {
        throw TooLargeColumn
      }
      line match {
        //separator and not in quotes -> send column to builder
        case settings.separator :: tail if !innerQuotes => fieldTypes match {
          case Some(fieldType) :: fieldTypesTail => processLine(tail, "", false)(fieldTypesTail, builder.writeValue(buffer, fieldType))
          case None :: fieldTypesTail => processLine(tail, "", false)(fieldTypesTail, builder)
          case _ => throw new NumberOfValuesException(settings.dataTypes.size)
        }
        //quotes and not in quotes -> do not save this char -> go to quotes environment
        case settings.quotesChar :: tail if !innerQuotes && buffer.trim.isEmpty => processLine(tail, "", true)
        //escape char and then quotes char and in quotes -> save quotes
        case settings.escapeChar :: settings.quotesChar :: tail if innerQuotes => processLine(tail, buffer + settings.quotesChar, true)
        //quotes char and in quotes -> do not save this char -> go to normal environment
        case settings.quotesChar :: tail if innerQuotes => processLine(tail, buffer, false)
        //other char -> save it to buffer
        case x :: tail => processLine(tail, buffer + x, innerQuotes)
        //end of line
        //if quotes environment -> return the buffer for next processing and current builder (parsing of this column will be continue within next line)
        //if normal environment -> send column to builder -> call end line -> return new builder
        case Nil => if (innerQuotes) {
          (buffer + " ", fieldTypes, builder)
        } else fieldTypes match {
          case Some(fieldType) :: Nil => ("", Nil, builder.writeValue(buffer, fieldType))
          case None :: Nil => ("", Nil, builder)
          case _ => throw new NumberOfValuesException(settings.dataTypes.size)
        }
      }
    }
    processMultiLine()(settings.dataTypes, builder)
  }

  /**
    * Write CSV input stream chunks to a data source builder
    * Input stream is reading line by line. So only one (current) line is saved to the memory and then being parsed.
    *
    * @param is CSV input stream. This method also consumes a compressed stream by gzip, bzip2 or zip, but for decompression it should be specified in the settings.
    * @return It returns DataSourceDetail class if the input stream has been read and processed by the data source builder successfully.
    */
  def write(is: InputStream) = try {
    //decompress input stream if compressed
    val decompressedIs = settings.compression.map(CompressionType.decompressInputStream(is)).getOrElse(is)
    //add linebounded decorator for the decompressed input stream. It prevents too large lines and DoS attacks.
    tryClose(Source.fromInputStream(new LineBoundedInputStream(decompressedIs, 1 * 1000 * 1000), settings.encoding.name())) { source =>
      val linesIterator = source.getLines()
      //build data source
      dataSourceBuilder.build { fieldBuilder =>
        //create line iterator, create field builder and process all lines
        processRow(linesIterator, new FieldDataBuilderCsvAdapter(fieldBuilder)).asInstanceOf[FieldDataBuilderCsvAdapter].builder.build { valueBuilder =>
          Stream
            .continually(linesIterator.hasNext)
            .takeWhile(_ == true)
            .foldLeft(valueBuilder) { (valueBuilder, _) =>
              val values = processRow(linesIterator, new ValueDataBuilderCsvAdapter()).asInstanceOf[ValueDataBuilderCsvAdapter].buffer
              if (values.nonEmpty || linesIterator.hasNext) valueBuilder.addInstance(values) else valueBuilder
            }
            .build
        }
      }
    }
  } finally {
    is.close()
  }

}

object CsvInputParser {

  private val maxColumnSize = Validators.tableColMaxlen

  case class Settings(separator: Char,
                      encoding: Charset,
                      quotesChar: Char,
                      escapeChar: Char,
                      locale: Locale,
                      compression: Option[CompressionType],
                      nullValues: Set[String],
                      dataTypes: List[Option[FieldType]])


  object Exceptions {

    sealed abstract class CsvParserException(msg: String) extends Exception("An error during CSV parsing: " + msg) with StatusCodeException.BadRequest

    object EmptyColName extends CsvParserException("Some column has empty name.")

    object TooLargeColumn extends CsvParserException(s"Some column is too large. Maximum number of characters per column is $maxColumnSize.")

    class InvalidNumericValueException(value: String, col: Int) extends CsvParserException(s"Invalid numeric value '$value' in column $col.")

    class NumberOfValuesException(expected: Int) extends CsvParserException(s"Some line has invalid number of values (expected: $expected).")

  }

}