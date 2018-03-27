/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl

import java.nio.charset.Charset
import java.util.{Date, Locale}

import cz.vse.easyminer.core.{JsonDeserializationNotSupported, JsonDeserializeAttributeException, JsonSerializationNotSupported}
import cz.vse.easyminer.data._
import org.apache.jena.riot.Lang
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import spray.json.{JsString, _}

import scala.util.Try

/**
  * Converters from object to json and vice versa
  * It is self-documented
  */
object JsonFormatters extends DefaultJsonProtocol {

  object JsonDate {

    implicit object DateJsonFormat extends JsonFormat[Date] {
      def write(x: Date) = JsString(new DateTime(x).toString(ISODateTimeFormat.basicDateTimeNoMillis()))

      def read(value: JsValue) = value match {
        case JsString(date) => Try(DateTime.parse(date, ISODateTimeFormat.basicDateTimeNoMillis())).getOrElse(throw new JsonDeserializeAttributeException("Date", "Date should have ISO8601 format (yyyyMMdd'T'HHmmssZ)")).toDate
        case _ => throw new JsonDeserializeAttributeException("Date", "Date should be a string.")
      }
    }

  }

  object JsonDataSourceType {

    implicit object JsonDataSourceTypeFormat extends RootJsonFormat[DataSourceType] {
      def read(json: JsValue): DataSourceType = json match {
        case JsString("limited") => LimitedDataSourceType
        case JsString("unlimited") => UnlimitedDataSourceType
        case _ => throw new JsonDeserializeAttributeException("dbType", "It should be 'limited' or 'unlimited'.")
      }

      def write(obj: DataSourceType): JsValue = obj match {
        case LimitedDataSourceType => JsString("limited")
        case UnlimitedDataSourceType => JsString("unlimited")
      }
    }

  }

  object JsonCharset {

    implicit object JsonCharsetFormat extends RootJsonFormat[Charset] {
      def read(json: JsValue): Charset = json match {
        case JsString(charset) => Charset.forName(charset)
        case _ => throw new JsonDeserializeAttributeException("encoding", "It should be a valid charset string.")
      }

      def write(obj: Charset): JsValue = throw JsonSerializationNotSupported
    }

  }

  object JsonLocale {

    implicit object JsonLocaleFormat extends RootJsonFormat[Locale] {
      def read(json: JsValue): Locale = json match {
        case JsString(locale) => Locale.forLanguageTag(locale)
        case _ => throw new JsonDeserializeAttributeException("locale", "It should be a valid language tag.")
      }

      def write(obj: Locale): JsValue = throw JsonSerializationNotSupported
    }

  }

  object JsonRdfLang {

    implicit object JsonRdfLangFormat extends RootJsonFormat[Lang] {
      def write(obj: Lang): JsValue = throw JsonSerializationNotSupported

      def read(json: JsValue): Lang = json match {
        case JsString(x) if x.toLowerCase() == Lang.NT.getName.toLowerCase => Lang.NT
        case JsString(x) if x.toLowerCase() == Lang.NQ.getName.toLowerCase => Lang.NQ
        case JsString(x) if x.toLowerCase() == Lang.TTL.getName.toLowerCase => Lang.TTL
        case _ => throw new JsonDeserializeAttributeException("rdf-lang", "It should be a valid rdf format (N-Triples, N-Quads, Turtle).")
      }
    }

  }

  object JsonFieldType {

    implicit object JsonFieldTypeFormat extends RootJsonFormat[FieldType] {
      def read(json: JsValue): FieldType = json match {
        case JsString("nominal") => NominalFieldType
        case JsString("numeric") => NumericFieldType
        case _ => throw new JsonDeserializeAttributeException("fieldType", "It should be 'nominal' or 'numeric'.")
      }

      def write(obj: FieldType): JsValue = obj match {
        case NominalFieldType => JsString("nominal")
        case NumericFieldType => JsString("numeric")
      }
    }

  }

  object JsonValue {

    implicit object JsonValueFormat extends RootJsonFormat[Value] {
      def read(json: JsValue): Value = json match {
        case JsNumber(value) => NumericValue(value.toString(), value.toDouble)
        case JsString(value) => NominalValue(value)
        case _ => NullValue
      }

      def write(obj: Value): JsValue = obj match {
        case NominalValue(value) => JsString(value)
        case NumericValue(original, _) => JsString(original)
        case NullValue => JsNull
      }
    }

  }

  object JsonValueDetail {

    implicit object JsonValueDetailFormat extends RootJsonFormat[ValueDetail] {
      def read(json: JsValue): ValueDetail = throw JsonDeserializationNotSupported

      def write(obj: ValueDetail): JsValue = obj match {
        case NominalValueDetail(id, _, value, frequency) => JsObject("id" -> JsNumber(id), "value" -> JsString(value), "frequency" -> JsNumber(frequency))
        case NumericValueDetail(id, _, original, value, frequency) => JsObject("id" -> JsNumber(id), "value" -> JsString(original), "frequency" -> JsNumber(frequency))
        case NullValueDetail(id, _, frequency) => JsObject("id" -> JsNumber(id), "value" -> JsNull, "frequency" -> JsNumber(frequency))
      }
    }

  }

  object JsonValueInterval {

    implicit object JsonValueIntervalFormat extends RootJsonFormat[ValueInterval] {
      def read(json: JsValue): ValueInterval = throw JsonDeserializationNotSupported

      def write(obj: ValueInterval): JsValue = obj match {
        case NumericValueInterval(from, to, frequency) => JsObject(
          "from" -> JsNumber(from.value),
          "to" -> JsNumber(to.value),
          "fromInclusive" -> JsBoolean(from.isInstanceOf[InclusiveIntervalBorder]),
          "toInclusive" -> JsBoolean(to.isInstanceOf[InclusiveIntervalBorder]),
          "frequency" -> JsNumber(frequency)
        )
        case NullValueInterval(frequency) => JsObject(
          "from" -> JsNull,
          "to" -> JsNull,
          "fromInclusive" -> JsBoolean(true),
          "toInclusive" -> JsBoolean(true),
          "frequency" -> JsNumber(frequency)
        )
      }
    }

  }

  object JsonCompressionType {

    implicit object JsonCompressionTypeFormat extends RootJsonFormat[CompressionType] {
      def read(json: JsValue): CompressionType = json match {
        case JsString("zip") => ZipCompressionType
        case JsString("gzip") => GzipCompressionType
        case JsString("bzip2") => Bzip2CompressionType
        case _ => throw new JsonDeserializeAttributeException("compression", "It should be 'zip', 'gzip' or 'bzip2'.")
      }

      def write(obj: CompressionType): JsValue = throw JsonSerializationNotSupported
    }

  }

  object JsonFieldNumericDetail {

    implicit val JsonFieldNumericDetailFormat: RootJsonFormat[FieldNumericDetail] = jsonFormat4(FieldNumericDetail)

  }

  object JsonFieldDetail {

    import JsonFieldType._

    implicit object JsonFieldDetailFormat extends RootJsonFormat[FieldDetail] {
      def read(json: JsValue): FieldDetail = throw JsonDeserializationNotSupported

      def write(obj: FieldDetail): JsValue = {
        val fieldDetailMap = jsonFormat8(FieldDetail).write(obj).asJsObject.fields
        JsObject(fieldDetailMap.filterNot(x => x._1.endsWith("Nominal") || x._1.endsWith("Numeric")) +("uniqueValuesSize" -> JsNumber(obj.uniqueValuesSize), "support" -> JsNumber(obj.support)))
      }
    }

  }

  object JsonDataSourceDetail {

    import JsonDataSourceType._

    implicit val JsonDataSourceDetailFormat: RootJsonFormat[DataSourceDetail] = jsonFormat5(DataSourceDetail.apply)

  }

  object JsonAggregatedInstanceItem {

    import JsonValue._

    implicit val JsonAggregatedInstanceItemFormat: RootJsonFormat[AggregatedInstanceItem] = jsonFormat2(AggregatedInstanceItem)

  }

  object JsonAggregatedInstance {

    import JsonAggregatedInstanceItem._

    implicit val JsonAggregatedInstanceFormat: RootJsonFormat[AggregatedInstance] = jsonFormat2(AggregatedInstance)

  }

}