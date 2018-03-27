package cz.vse.easyminer.data.hadoop

import cz.vse.easyminer.core.hadoop.Hdfs
import cz.vse.easyminer.data.hadoop.DataHdfs.CsvWriter
import cz.vse.easyminer.data.hadoop.DataHdfs.Exceptions.FileExists
import cz.vse.easyminer.data.{NominalValue, NullValue, NumericValue, Value}
import org.apache.hadoop.fs.{FSDataOutputStream, FileSystem}

/**
 * Created by propan on 30. 10. 2015.
 */
class DataHdfs private(protected[this] val fileSystem: FileSystem) extends Hdfs {

  def useCsvWriter[T](fileName: String, delimiter: String, escape: String)(f: CsvWriter => T): T = useCsvWriters(delimiter, escape, fileName) { csvWriters =>
    f(csvWriters.head)
  }

  def useCsvWriters[T](delimiter: String, escape: String, fileNames: String*)(f: Seq[CsvWriter] => T): T = {
    for (fileName <- fileNames) {
      if (fileExists(fileName)) {
        throw new FileExists(fileName)
      }
    }
    val writers = fileNames.map(fileName => fileSystem.create(filePath(fileName), false))
    try {
      f(writers.map(writer => new CsvWriter(writer, delimiter, escape)))
    } finally {
      writers.foreach(_.close())
    }
  }

}

object DataHdfs {

  def apply[T](f: DataHdfs => T): T = {
    Hdfs(f, fs => new DataHdfs(fs))
  }

  class CsvWriter(fSDataOutputStream: FSDataOutputStream, delimiter: String, escape: String) {

    def writeLine(line: Seq[Value]) = fSDataOutputStream.write(
      (line.iterator.map {
        case NumericValue(original, value) => value.toString.replace(escape, escape + escape).replace(delimiter, escape + delimiter)
        case NominalValue(value) => value.replace(escape, escape + escape).replace(delimiter, escape + delimiter)
        case NullValue => ""
      }.mkString(delimiter) + "\n").getBytes
    )

  }

  object Exceptions {

    class FileExists(fileName: String) extends Exception(s"File '$fileName' already exists in HDFS.")

  }

}