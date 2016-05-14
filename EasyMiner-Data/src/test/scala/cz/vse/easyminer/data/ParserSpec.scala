package cz.vse.easyminer.data

import java.io._
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.{ZipEntry, ZipOutputStream}

import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.data.impl.parser.CsvInputParser.Settings
import cz.vse.easyminer.data.impl.parser.{CsvInputParser, LineBoundedInputStream, LineParser}
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable.ListBuffer
import scala.io.Source

/**
  * Created by propan on 14. 8. 2015.
  */
class ParserSpec extends FlatSpec with Matchers {

  val defaultCsvSettings = Settings(
    ',',
    Charset.forName("UTF-8"),
    '"',
    '"',
    new Locale("cs"),
    None,
    Set("null", ""),
    List(NumericFieldType, NumericFieldType, NumericFieldType, NominalFieldType, NumericFieldType, NumericFieldType, NumericFieldType, NominalFieldType).map(Some.apply)
  )

  case class DataSourceBuilderData(attributes: ListBuffer[Field], instances: ListBuffer[Seq[Value]], builder: DataSourceBuilder)

  def newDataSourceBuilder: DataSourceBuilderData = {
    val finalInstances = ListBuffer.empty[Seq[Value]]
    val finalAttributes = ListBuffer.empty[Field]
    val builder = new DataSourceBuilder {

      val name: String = "test"

      class TestValueBuilder(val dataSource: DataSourceDetail) extends ValueBuilder {
        val fields: Seq[FieldDetail] = finalAttributes.map(x => FieldDetail(0, 0, x.name, x.`type`, 0))

        def addInstance(values: Seq[Value]): ValueBuilder = {
          if (values.size == finalAttributes.size) {
            finalInstances += values.zip(finalAttributes).filter {
              case (NominalValue(_), Field(_, NominalFieldType)) => true
              case (NumericValue(_), Field(_, NumericFieldType)) => true
              case (NullValue, _) => true
              case _ => false
            }.map(_._1)
          }
          this
        }

        def build: DataSourceDetail = dataSource
      }

      class TestFieldBuilder(val dataSource: DataSourceDetail) extends FieldBuilder {
        def field(attribute: Field): FieldBuilder = {
          finalAttributes += attribute
          this
        }

        def build(f: (ValueBuilder) => DataSourceDetail): DataSourceDetail = f(new TestValueBuilder(dataSource))
      }

      def build(f: (FieldBuilder) => DataSourceDetail): DataSourceDetail = {
        f(new TestFieldBuilder(DataSourceDetail(0, name, LimitedDataSourceType, 0, true)))
      }
    }
    DataSourceBuilderData(finalAttributes, finalInstances, builder)
  }

  "CSVParser" should "parse large CSV without exceptions" in {
    val DataSourceBuilderData(attributes, instances, builder) = newDataSourceBuilder
    val csvHandler = new CsvInputParser(builder, defaultCsvSettings)
    csvHandler.write(getClass.getResourceAsStream("/test.csv"))
    attributes.size shouldBe 8
    (attributes match {
      case Seq(Field("loan_id", NumericFieldType), Field("age", NumericFieldType), Field("salary", NumericFieldType), Field("district", NominalFieldType), Field("amount", NumericFieldType), Field("payments", NumericFieldType), Field("duration", NumericFieldType), Field("rating", NominalFieldType)) => true
      case _ => false
    }) shouldBe true
    instances.size shouldBe 6181
    instances.foreach(_.size shouldBe 8)
    (instances(1) match {
      case Seq(NumericValue(2257.0), NullValue, NullValue, NominalValue("""Praha "Bohnice""""), NumericValue(30.123), NumericValue(500.0), NumericValue(60.0), NominalValue("D")) => true
      case _ => false
    }) shouldBe true
  }

  it should "parse CSV with special characters and more localizations" in {
    val DataSourceBuilderData(attributes, instances, builder) = newDataSourceBuilder
    val csvHandler1 = new CsvInputParser(builder, defaultCsvSettings.copy(locale = new Locale("en", "US"), dataTypes = List(NominalFieldType, NumericFieldType).map(Some.apply)))
    val csv =
      """a,b
        " "", ","30,000.1234"
        abc"de"f,-15.1
        "aaaa

        bbbb",
        ěščřžýáíé,1.11111
      """
    csvHandler1.write(new ByteArrayInputStream(csv.getBytes("UTF-8")))
    attributes.size shouldBe 2
    (instances match {
      case Seq(
      Seq(NominalValue("\","), NumericValue(30000.1234)),
      Seq(NominalValue("abc\"de\"f"), NumericValue(-15.1)),
      Seq(NominalValue("aaaa  bbbb"), NullValue),
      Seq(NominalValue("ěščřžýáíé"), NumericValue(1.11111))
      ) => true
      case _ => false
    }) shouldBe true
    attributes.clear()
    instances.clear()
    val csvHandler2 = new CsvInputParser(builder, csvHandler1.settings.copy(encoding = Charset.forName("Cp1250")))
    csvHandler2.write(new ByteArrayInputStream(csv.getBytes(csvHandler2.settings.encoding)))
    (instances(3).head match {
      case NominalValue("ěščřžýáíé") => true
      case _ => false
    }) shouldBe true
  }

  it should "convert numeric value to null if bad numeric parsing" in {
    val DataSourceBuilderData(attributes, instances, builder) = newDataSourceBuilder
    val csvHandler = new CsvInputParser(builder, defaultCsvSettings.copy(dataTypes = List(NumericFieldType).map(Some.apply)))
    val csv =
      """a
        1
        2
        ab
        3
        4
        a5aa
      """
    csvHandler.write(new ByteArrayInputStream(csv.getBytes("UTF-8")))
    attributes.foreach(_.`type` shouldBe NumericFieldType)
    val normInstances = instances.map(_.head)
    normInstances.collect {
      case NumericValue(x) => x
    }.sum shouldBe 10
    normInstances.size shouldBe 6
    normInstances(2) shouldBe NullValue
    normInstances(5) shouldBe NullValue
  }

  it should "parse different localized numbers" in {
    val DataSourceBuilderData(_, instances, builder) = newDataSourceBuilder
    val csvHandler = new CsvInputParser(builder, defaultCsvSettings.copy(locale = new Locale("cs"), dataTypes = List(Some(NumericFieldType))))
    val csv =
      """a
        "30 000,1234"
        25.25
        "30,000.25"
      """
    csvHandler.write(new ByteArrayInputStream(csv.getBytes("UTF-8")))
    (instances match {
      case Seq(Seq(NumericValue(30000.1234)), Seq(NumericValue(25.0)), Seq(NumericValue(30.0))) => true
      case _ => false
    }) shouldBe true
  }

  it should "parse compressed input stream" in {
    val DataSourceBuilderData(attributes, instances, builder) = newDataSourceBuilder
    val is = getClass.getResourceAsStream("/test.csv")
    val uncompressed = try {
      val baos = new ByteArrayOutputStream()
      Stream.continually(is.read()).takeWhile(_ != -1).foreach(baos.write)
      baos.close()
      baos.toByteArray
    } finally {
      is.close()
    }
    val compressors: List[(CompressionType, OutputStream => OutputStream)] = List(
      GzipCompressionType -> { compressed =>
        new CompressorStreamFactory().createCompressorOutputStream("gz", compressed)
      },
      Bzip2CompressionType -> { compressed =>
        new CompressorStreamFactory().createCompressorOutputStream("bzip2", compressed)
      },
      ZipCompressionType -> { compressed =>
        val zipos = new ZipOutputStream(compressed)
        zipos.putNextEntry(new ZipEntry("test"))
        zipos
      }
    )
    for ((ctype, compressorBuilder) <- compressors) {
      instances.clear()
      attributes.clear()
      val cbaos = new ByteArrayOutputStream()
      tryClose(compressorBuilder(cbaos)) { compressor =>
        compressor.write(uncompressed)
      }
      new CsvInputParser(builder, defaultCsvSettings.copy(compression = Some(ctype))).write(new ByteArrayInputStream(cbaos.toByteArray))
      instances.size shouldBe 6181
      attributes.size shouldBe 8
    }
  }

  "LineBoundedInputStream" should "return original message" in {
    tryClose(Source.fromInputStream(new LineBoundedInputStream(getClass.getResourceAsStream("/test.csv"), 1000), "UTF-8")) { source =>
      source.getLines().size shouldBe 6182
    }
  }

  it should "throw exception if large line" in intercept[IndexOutOfBoundsException] {
    tryClose(Source.fromInputStream(new LineBoundedInputStream(getClass.getResourceAsStream("/test.csv"), 30), "UTF-8")) { source =>
      source.getLines().size
    }
  }

  "LineParser" should "parse only first limited lines" in {
    new String(LineParser(10)(None).parse(getClass.getResourceAsStream("/test.csv"))).count(_ == '\n') shouldBe 10
    new String(LineParser(7000)(None).parse(getClass.getResourceAsStream("/test.csv"))).count(_ == '\n') shouldBe 6182
    val uncompressed = tryClose(getClass.getResourceAsStream("/test.csv")) { is =>
      val baos = new ByteArrayOutputStream()
      Stream.continually(is.read()).takeWhile(_ != -1).foreach(baos.write)
      baos.toByteArray
    }
    val cbaos = new ByteArrayOutputStream()
    tryClose(new CompressorStreamFactory().createCompressorOutputStream("gz", cbaos)) { os =>
      os.write(uncompressed)
    }
    new String(LineParser(1)(Some(GzipCompressionType)).parse(new ByteArrayInputStream(cbaos.toByteArray))).length shouldBe 62
  }

  it should "throw exception if lines has too many bytes" in intercept[IndexOutOfBoundsException] {
    LineParser(10, 300)(None).parse(getClass.getResourceAsStream("/test.csv"))
  }

}
