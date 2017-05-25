/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

import java.io.InputStream
import java.util.zip.ZipInputStream

import org.apache.commons.compress.compressors.CompressorStreamFactory

/**
 * Created by Vaclav Zeman on 5. 9. 2015.
 */
sealed trait CompressionType

object Bzip2CompressionType extends CompressionType

object GzipCompressionType extends CompressionType

object ZipCompressionType extends CompressionType

object CompressionType {

  def decompressInputStream(is: InputStream)(compressionType: CompressionType) = compressionType match {
    case ZipCompressionType =>
      val zis = new ZipInputStream(is)
      zis.getNextEntry
      zis
    case GzipCompressionType => new CompressorStreamFactory().createCompressorInputStream("gz", is)
    case Bzip2CompressionType => new CompressorStreamFactory().createCompressorInputStream("bzip2", is)
  }

}