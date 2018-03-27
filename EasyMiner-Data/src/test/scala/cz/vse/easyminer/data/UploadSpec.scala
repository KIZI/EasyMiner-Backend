package cz.vse.easyminer
package data

import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.Concurrency.FutureOps
import cz.vse.easyminer.data.impl.parser.{LazyByteBufferInputStream, LineParser}
import cz.vse.easyminer.data.impl.{PreviewUploadActor, UploadActor}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Try}

/**
 * Created by propan on 17. 8. 2015.
 */
class UploadSpec extends FlatSpec with Matchers {

  implicit val timeout = Timeout(5 seconds)

  implicit val ec = actorSystem.dispatcher

  def readerWriter = new LazyByteBufferInputStream(1000, 5 seconds)

  def startUpload(bufferedWriter: BufferedWriter, futureDataSource: Future[DataSourceDetail])(f: ActorRef => Unit) = {
    val actor = actorSystem.actorOf(
      Props(
        new UploadActor(
          UUID.randomUUID.toString,
          bufferedWriter,
          futureDataSource,
          new DataSourceOps {

            def deleteDataSource(dataSourceId: Int): Unit = ()

            def getAllDataSources: List[DataSourceDetail] = Nil

            def getDataSource(dataSourceId: Int): Option[DataSourceDetail] = None

            def renameDataSource(dataSourceId: Int, newName: String): Unit = ()

            def getInstances(dataSourceId: Int, fieldIds: Seq[Int], offset: Int, limit: Int): Seq[Instance] = Nil
          }
        )
      )
    )
    try {
      f(actor)
    } finally {
      actorSystem.stop(actor)
    }
  }

  "Upload Actor" should "start and receive data" in {
    val rw = readerWriter
    val resultMockup = Future {
      val size = Stream.continually(rw.read()).takeWhile(_ != -1).count(_ => true)
      DataSourceDetail(0, "test", LimitedDataSourceType, size, true)
    }
    startUpload(rw, resultMockup) { actor =>
      for (x <- 0 until 5) {
        (actor ? UploadActor.Request.Data(Array.fill[Byte](100)(1))).quickResultWithTimeout shouldBe UploadActor.Response.InProgress
      }
      (actor ? UploadActor.Request.Finish).quickResultWithTimeout shouldBe UploadActor.Response.InProgress
      while (!resultMockup.isCompleted) {
        Thread.sleep(100)
      }
      (actor ? UploadActor.Request.Finish).quickResultWithTimeout shouldBe UploadActor.Response.Finished(DataSourceDetail(0, "test", LimitedDataSourceType, 500, true))
    }
  }

  it should "call 'slow down' if the uploading process is too fast or buffer is full" in {
    val rw = readerWriter
    val resultMockup = Future {
      val size = Stream.continually(rw.read()).takeWhile(_ != -1).count { _ =>
        Thread.sleep(1)
        true
      }
      DataSourceDetail(0, "test", LimitedDataSourceType, size, true)
    }
    startUpload(rw, resultMockup) { actor =>
      (actor ? UploadActor.Request.Data(Array.fill[Byte](1001)(1))).quickResultWithTimeout shouldBe UploadActor.Response.SlowDown
      (actor ? UploadActor.Request.Data(Array.fill[Byte](500)(1))).quickResultWithTimeout shouldBe UploadActor.Response.InProgress
      (actor ? UploadActor.Request.Data(Array.fill[Byte](500)(1))).quickResultWithTimeout shouldBe UploadActor.Response.InProgress
      (actor ? UploadActor.Request.Data(Array.fill[Byte](500)(1))).quickResultWithTimeout shouldBe UploadActor.Response.SlowDown
      (actor ? UploadActor.Request.Finish).quickResultWithTimeout shouldBe UploadActor.Response.InProgress
      while (!resultMockup.isCompleted) {
        Thread.sleep(100)
      }
      (actor ? UploadActor.Request.Finish).quickResultWithTimeout shouldBe UploadActor.Response.Finished(DataSourceDetail(0, "test", LimitedDataSourceType, 1000, true))
    }
  }

  it should "return an error if the reader throw some exception" in {
    val rw = readerWriter
    val resultMockup1 = Future {
      throw new Exception("error")
      DataSourceDetail(0, "test", LimitedDataSourceType, 0, true)
    }
    val resultMockup2 = Future {
      DataSourceDetail(0, "test", LimitedDataSourceType, 0, true)
    }
    startUpload(rw, resultMockup1) { actor =>
      Try((actor ? UploadActor.Request.Data(Array.fill[Byte](100)(1))).quickResultWithTimeout).failed.foreach(_.getMessage shouldBe "error")
    }
    startUpload(rw, resultMockup2) { actor =>
      Try((actor ? UploadActor.Request.Data(Array.fill[Byte](100)(1))).quickResultWithTimeout).failed.foreach(_ shouldBe UploadActor.Exceptions.ResultIsTooSoon)
    }
  }

  "Preview upload actor" should "be able to accept data and return several top lines" in {
    val lbbis = new LazyByteBufferInputStream(10 * 1000 * 1000, 10 seconds)
    val futureResult = Future {
      LineParser(10)(None).parse(lbbis)
    }
    val actor = actorSystem.actorOf(Props(new PreviewUploadActor("test", lbbis, futureResult)))
    (actor ? PreviewUploadActor.Request.Data("a\na\na\na\na\n".getBytes)).quickResultWithTimeout shouldBe PreviewUploadActor.Response.InProgress
    Thread.sleep(500)
    (actor ? PreviewUploadActor.Request.Data("a\na\na\n".getBytes)).quickResultWithTimeout shouldBe PreviewUploadActor.Response.InProgress
    Thread.sleep(500)
    (actor ? PreviewUploadActor.Request.Data("a\na\na\na\na\na\na\na\na\n".getBytes)).quickResultWithTimeout shouldBe PreviewUploadActor.Response.InProgress
    Thread.sleep(500)
    (limitedRepeatUntil[Any](10)(_.isInstanceOf[PreviewUploadActor.Response.Finished])((actor ? PreviewUploadActor.Request.Data("a\na\na\n".getBytes)).quickResultWithTimeout) match {
      case PreviewUploadActor.Response.Finished(a) => new String(a).count(_ == 'a')
      case x => 0
    }) shouldBe 10
  }

  it should "return all lines if end of stream" in {
    val lbbis = new LazyByteBufferInputStream(10 * 1000 * 1000, 10 seconds)
    val futureResult = Future {
      LineParser(10)(None).parse(lbbis)
    }
    val actor = actorSystem.actorOf(Props(new PreviewUploadActor("test", lbbis, futureResult)))
    (actor ? PreviewUploadActor.Request.Data("a\na\na\na\na\n".getBytes)).quickResultWithTimeout shouldBe PreviewUploadActor.Response.InProgress
    (limitedRepeatUntil[Any](10, 500 milliseconds)(_.isInstanceOf[PreviewUploadActor.Response.Finished])((actor ? PreviewUploadActor.Request.Finish).quickResultWithTimeout) match {
      case PreviewUploadActor.Response.Finished(a) => new String(a).count(_ == 'a')
      case x => 0
    }) shouldBe 5
  }

  it should "return a failure if the future object threw an exception" in {
    val lbbis = new LazyByteBufferInputStream(10, 10 seconds)
    val futureResult: Future[Array[Byte]] = Future {
      throw new Exception("error")
    }
    val actor = actorSystem.actorOf(Props(new PreviewUploadActor("test", lbbis, futureResult)))
    (Try((actor ? PreviewUploadActor.Request.Data(Array.fill[Byte](10)(1))).quickResultWithTimeout) match {
      case Failure(ex) => ex.getMessage
      case _ => ""
    }) shouldBe "error"
  }

}
