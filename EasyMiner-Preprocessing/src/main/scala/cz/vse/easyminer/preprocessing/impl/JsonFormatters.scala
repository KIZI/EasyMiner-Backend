package cz.vse.easyminer.preprocessing.impl

import cz.vse.easyminer.core.{JsonDeserializationNotSupported, JsonDeserializeAttributeException}
import cz.vse.easyminer.data.{InclusiveIntervalBorder, NullValueInterval, NumericValueInterval, ValueInterval}
import cz.vse.easyminer.preprocessing._
import spray.json._

import scala.util.{Failure, Success, Try}

/**
  * Created by propan on 18. 8. 2015.
  */
object JsonFormatters extends DefaultJsonProtocol {

  object JsonDatasetType {

    implicit object JsonDatasetTypeFormat extends RootJsonFormat[DatasetType] {
      def read(json: JsValue): DatasetType = json match {
        case JsString("limited") => LimitedDatasetType
        case JsString("unlimited") => UnlimitedDatasetType
        case _ => throw new JsonDeserializeAttributeException("dbType", "It should be 'limited' or 'unlimited'.")
      }

      def write(obj: DatasetType): JsValue = obj match {
        case LimitedDatasetType => JsString("limited")
        case UnlimitedDatasetType => JsString("unlimited")
      }
    }

  }

  object JsonValueDetail {

    implicit val JsonValueDetailFormat: RootJsonFormat[ValueDetail] = jsonFormat4(ValueDetail)

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

  object JsonTry {

    implicit def jsonTryFormat[T: JsonFormat] = new RootJsonFormat[Try[T]] {
      def write(obj: Try[T]): JsValue = obj match {
        case Success(x) => x.toJson
        case Failure(th) => JsObject("error" -> JsString(th.getClass.getSimpleName), "message" -> JsString(th.getMessage))
      }

      def read(json: JsValue): Try[T] = throw JsonDeserializationNotSupported
    }

  }

  object JsonAttributeDetail {

    implicit val JsonAttributeDetailFormat: RootJsonFormat[AttributeDetail] = jsonFormat6(AttributeDetail.apply)

  }

  object JsonDatasetDetail {

    import JsonDatasetType._

    implicit val JsonDatasetDetailFormat: RootJsonFormat[DatasetDetail] = jsonFormat6(DatasetDetail.apply)

  }

}