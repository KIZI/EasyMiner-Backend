package cz.vse.easyminer.data

import java.io.ByteArrayOutputStream

import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.data.impl.JsonFormatters
import org.scalatest.{DoNotDiscover, FlatSpec, Matchers}
import org.slf4j.LoggerFactory
import spray.http.HttpResponse
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.BasicMarshallers
import spray.httpx.unmarshalling.{BasicUnmarshallers, Unmarshaller}
import spray.json._

import scala.annotation.tailrec

/**
 * Created by propan on 25. 9. 2015.
 */
@DoNotDiscover
class UploadServiceSpec(restSpec: RestSpec) extends FlatSpec with Matchers {

  import BasicMarshallers._
  import BasicUnmarshallers._
  import DefaultJsonProtocol._
  import JsonFormatters.JsonDataSourceDetail._
  import SprayJsonSupport._
  import restSpec._

  val logger = LoggerFactory.getLogger("cz.vse.easyminer.data.UploadServiceSpec")

  lazy val csvSettings: Map[String, JsValue] = Map(
    "name" -> JsString("test"),
    "mediaType" -> JsString("csv"),
    "dbType" -> JsString("limited"),
    "separator" -> JsString(","),
    "encoding" -> JsString("UTF-8"),
    "quotesChar" -> JsString("\""),
    "escapeChar" -> JsString("\""),
    "locale" -> JsString("cs"),
    "nullValues" -> JsArray(JsString(""), JsString("null")),
    "dataTypes" -> JsArray(JsNull, JsString("numeric"), JsString("numeric"), JsString("nominal"), JsString("numeric"), JsString("numeric"), JsString("numeric"), JsString("nominal"))
  )

  def createUploadTask(settings: Map[String, JsValue]) = authorizedRequest(Post("/api/v1/upload/start", JsObject(settings))) ~> route ~> check {
    response.status.intValue shouldBe 200
    responseAs[String]
  }

  @tailrec
  private def uploadResult[T](url: String)(implicit unmarshaller: Unmarshaller[T]): (Int, T) = {
    val (responseCode, data) = authorizedRequest(Post(url)) ~> route ~> check {
      response.status.intValue -> response
    }
    if (responseCode == 202) {
      if (data.entity.nonEmpty) {
        logger.trace("Uploading status: " + data.entity.asString)
      }
      Thread.sleep(100)
      uploadResult[T](url)
    } else unmarshaller(data.entity) match {
      case Left(x) => throw new Exception("Unmarshaller exception.")
      case Right(x) => responseCode -> x
    }
  }

  "Upload Service" should "be able to upload some data" in {
    authorizedRequest(Post("/api/v1/upload/start")) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
    authorizedRequest(Post("/api/v1/upload/start", JsObject("name" -> JsString("test")).toString())) ~> route ~> check {
      response.status.intValue shouldBe 415
    }
    authorizedRequest(Post("/api/v1/upload/start", JsObject("name" -> JsString("test")))) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
    authorizedRequest(Post("/api/v1/upload/start", JsObject("name" -> JsString("test"), "mediaType" -> JsString("csv"), "dbType" -> JsString("limited")))) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
    val uploadId = createUploadTask(csvSettings)
    tryClose(getClass.getResourceAsStream("/test.csv")) { inputStream =>
      Stream.continually(inputStream.read()).takeWhile(_ != -1).grouped(1000).foreach { window =>
        tryClose(new ByteArrayOutputStream()) { baos =>
          window foreach baos.write
          val byteArray = baos.toByteArray
          authorizedRequest(Post(s"/api/v1/upload/$uploadId", byteArray)) ~> route ~> check {
            response.status.intValue shouldBe 202
          }
        }
      }
    }
    authorizedRequest(Post(s"/api/v1/upload/$uploadId")) ~> route ~> check {
      response.status.intValue shouldBe 202
    }
    val (responseCode, data) = uploadResult[String](s"/api/v1/upload/$uploadId")
    responseCode shouldBe 200
    data.parseJson.convertTo[DataSourceDetail] shouldBe DataSourceDetail(1, "test", LimitedDataSourceType, 6181, true)
  }

  it should "return error if some exception was thrown during the upload process" taggedAs RestSpec.SlowTest in {
    val newCsvSettings = csvSettings + ("dataTypes" -> JsArray(JsString("nominal"), JsString("numeric"))) + ("name" -> JsString("test2"))
    val dataList = List(
      " ",
      """a,
         a,1
         b,2
      """,
      """a,b
         a
         b
      """,
      """a,b
         a,b
      """
    )
    for (data <- dataList) {
      val uploadId = createUploadTask(newCsvSettings)
      authorizedRequest(Post(s"/api/v1/upload/$uploadId", data)) ~> route ~> check {
        response.status.intValue shouldBe 202
      }
      uploadResult[String](s"/api/v1/upload/$uploadId")._1 shouldBe 400
    }
    val uploadId = createUploadTask(newCsvSettings)
    for (_ <- 0 until 10) {
      authorizedRequest(Post(s"/api/v1/upload/$uploadId", Array.fill[Byte](1000000)(10))) ~> route ~> check {
        response.status.intValue shouldBe 202
      }
    }
    authorizedRequest(Post(s"/api/v1/upload/$uploadId", Array.fill[Byte](1000)(10))) ~> route ~> check {
      response.status.intValue shouldBe 429
    }
    authorizedRequest(Post(s"/api/v1/upload/$uploadId")) ~> route ~> check {
      response.status.intValue shouldBe 202
    }
    uploadResult[String](s"/api/v1/upload/$uploadId")._1 shouldBe 400
    val uploadIdLongLine = createUploadTask(newCsvSettings)
    for (_ <- 0 until 2) {
      authorizedRequest(Post(s"/api/v1/upload/$uploadIdLongLine", Array.fill[Byte](1000000)(49))) ~> route ~> check {
        response.status.intValue shouldBe 202
      }
    }
    authorizedRequest(Post(s"/api/v1/upload/$uploadIdLongLine")) ~> route ~> check {
      response.status.intValue shouldBe 202
    }
    uploadResult[String](s"/api/v1/upload/$uploadIdLongLine")._1 shouldBe 400
    val uploadIdLongName = createUploadTask(newCsvSettings + ("name" -> JsString(Array.fill(300)("a").mkString)))
    authorizedRequest(Post(s"/api/v1/upload/$uploadIdLongName", Array.fill[Byte](1000)(49))) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
  }

  it should "be able to upload some data to hive" taggedAs(RestSpec.SlowTest, RestSpec.HiveTest) in {
    val uploadId = createUploadTask(csvSettings ++ Map(
      "dataTypes" -> JsArray(JsNull, JsString("numeric"), JsNull, JsString("nominal"), JsNull, JsNull, JsNull, JsString("nominal")),
      "name" -> JsString("test-hive"),
      "dbType" -> JsString("unlimited")
    ))
    tryClose(getClass.getResourceAsStream("/test.csv")) { inputStream =>
      Stream.continually(inputStream.read()).takeWhile(_ != -1).grouped(1000).foreach { window =>
        tryClose(new ByteArrayOutputStream()) { baos =>
          window foreach baos.write
          val byteArray = baos.toByteArray
          authorizedRequest(Post(s"/api/v1/upload/$uploadId", byteArray)) ~> route ~> check {
            response.status.intValue shouldBe 202
          }
        }
      }
    }
    authorizedRequest(Post(s"/api/v1/upload/$uploadId")) ~> route ~> check {
      response.status.intValue shouldBe 202
    }
    val (responseCode, data) = uploadResult[String](s"/api/v1/upload/$uploadId")
    responseCode shouldBe 200
    val dataSource = data.parseJson.convertTo[DataSourceDetail]
    dataSource.name shouldBe "test-hive"
    dataSource.`type` shouldBe UnlimitedDataSourceType
    dataSource.size shouldBe 6181
  }

  "Preview Upload Service" should "be able to upload some part of a file and return a preview" in {
    def checkResponse(data: Array[Byte]) = new String(data).count(_ == '\n') shouldBe 100
    val uploadId = authorizedRequest(Post("/api/v1/upload/preview/start", JsObject("mediaType" -> JsString("csv"), "maxLines" -> JsNumber(100)))) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[String]
    }
    tryClose(getClass.getResourceAsStream("/test.csv")) { inputStream =>
      Stream.continually(inputStream.read()).takeWhile(_ != -1).grouped(1000).foreach { window =>
        tryClose(new ByteArrayOutputStream()) { baos =>
          window foreach baos.write
          val byteArray = baos.toByteArray
          authorizedRequest(Post(s"/api/v1/upload/preview/$uploadId", byteArray)) ~> route ~> check {
            if (response.status.intValue == 200) {
              checkResponse(responseAs[Array[Byte]])
            } else {
              response.status.intValue should (be(202) or be(404))
            }
          }
        }
      }
    }
    val (responseCode, data) = uploadResult[Array[Byte]](s"/api/v1/upload/preview/$uploadId")
    if (responseCode == 200) {
      checkResponse(data)
    } else {
      responseCode shouldBe 404
    }
  }

  it should "return error if buffer is full" taggedAs RestSpec.SlowTest in {
    val uploadId = authorizedRequest(Post("/api/v1/upload/preview/start", JsObject("mediaType" -> JsString("csv"), "maxLines" -> JsNumber(100)))) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[String]
    }
    val uploadResponse = repeatUntil[HttpResponse]()(_.status.intValue != 202)(authorizedRequest(Post(s"/api/v1/upload/preview/$uploadId", Array.fill[Byte](1000)(49))) ~> route ~> check {
      response
    })
    uploadResponse.status.intValue shouldBe 400
    uploadResponse.entity.asString should include("Line is too large")
  }

}
