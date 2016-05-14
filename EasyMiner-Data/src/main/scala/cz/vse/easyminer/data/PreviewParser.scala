package cz.vse.easyminer.data

import java.io.InputStream

/**
 * Created by propan on 5. 9. 2015.
 */
trait PreviewParser {

  def parse(is: InputStream): Array[Byte]

}

object PreviewParser {

  def apply(parser: PreviewParser)(compressionType: Option[CompressionType]): PreviewParser = new PreviewParser {
    def parse(is: InputStream): Array[Byte] = parser.parse(compressionType.map(CompressionType.decompressInputStream(is)).getOrElse(is))
  }

}