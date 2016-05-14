package cz.vse.easyminer.core.hadoop

import java.io.InputStream

import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.Conf
import org.apache.hadoop.fs.{FileSystem, Path}

/**
 * Created by propan on 9. 3. 2016.
 */
trait Hdfs {

  protected[this] val fileSystem: FileSystem

  private val mainPath = new Path(Conf().get[String]("easyminer.hadoop.hdfs-main-path"))

  private def fileExists(path: Path): Boolean = fileSystem.exists(path)

  def fileExists(fileName: String): Boolean = fileExists(filePath(fileName))

  def filePath(fileName: String): Path = mainPath.suffix(s"/$fileName")

  def deleteFile(fileName: String): Unit = fileSystem.delete(filePath(fileName), false)

  def listFiles: Iterator[Path] = new Iterator[Path] {
    val it = fileSystem.listFiles(mainPath, false)
    var currentPath: Option[Path] = None

    private def hasNextPath = {
      while (it.hasNext && currentPath.isEmpty) {
        val lfs = it.next()
        if (lfs.isFile) currentPath = Some(lfs.getPath)
      }
      currentPath.isDefined
    }

    def hasNext: Boolean = hasNextPath

    def next(): Path = if (hasNextPath) {
      val returnedPath = currentPath.get
      currentPath = None
      returnedPath
    } else {
      Iterator.empty.next()
    }

  }

  def putFile(fileName: String, inputStream: InputStream) = {
    val writer = fileSystem.create(filePath(fileName), false)
    try {
      Stream.continually(inputStream.read()).takeWhile(_ != -1).foreach(writer.write)
    } finally {
      writer.close()
    }
  }

  def readFile[T](fileName: String)(f: InputStream => T) = {
    val is = fileSystem.open(filePath(fileName))
    try {
      f(is)
    } finally {
      is.close()
    }
  }

}

object Hdfs {

  Hadoop

  class Default private[Hdfs](protected[this] val fileSystem: FileSystem) extends Hdfs

  def apply[A](f: Hdfs => A): A = apply(f, fs => new Default(fs))

  def apply[A, B <: Hdfs](f: B => A, hdfsBuilder: FileSystem => B): A = tryClose(FileSystem.get(Hadoop.conf)) { fs =>
    f(hdfsBuilder(fs))
  }

  object Exceptions {

    object UnsupportedAuthType extends Exception("Unsupported hadoop authentication method (allowed methods are: 'simple' or 'kerberos').")

  }

}