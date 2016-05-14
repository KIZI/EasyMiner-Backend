package cz.vse.easyminer.data.impl

import java.nio.charset.Charset
import java.util.Locale

import cz.vse.easyminer.core.{JsonDeserializationNotSupported, JsonDeserializeAttributeException, JsonSerializationNotSupported}
import cz.vse.easyminer.data._
import spray.json._

/**
 * Created by propan on 18. 8. 2015.
 */
object JsonFormatters extends DefaultJsonProtocol {

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
      def read(json: JsValue): Value = throw JsonDeserializationNotSupported

      def write(obj: Value): JsValue = obj match {
        case NominalValue(value) => JsString(value)
        case NumericValue(value) => JsNumber(value)
        case NullValue => JsNull
      }
    }

  }

  object JsonValueDetail {

    implicit object JsonValueDetailFormat extends RootJsonFormat[ValueDetail] {
      def read(json: JsValue): ValueDetail = throw JsonDeserializationNotSupported

      def write(obj: ValueDetail): JsValue = obj match {
        case NominalValueDetail(id, _, value, frequency) => JsObject("id" -> JsNumber(id), "value" -> JsString(value), "frequency" -> JsNumber(frequency))
        case NumericValueDetail(id, _, value, frequency) => JsObject("id" -> JsNumber(id), "value" -> JsNumber(value), "frequency" -> JsNumber(frequency))
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

    implicit val JsonFieldDetailFormat: RootJsonFormat[FieldDetail] = jsonFormat5(FieldDetail)

  }

  object JsonDataSourceDetail {

    import JsonDataSourceType._

    implicit val JsonDataSourceDetailFormat: RootJsonFormat[DataSourceDetail] = jsonFormat5(DataSourceDetail)

  }

}