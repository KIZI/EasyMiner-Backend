package cz.vse.easyminer.data

import java.util.concurrent.TimeoutException

import cz.vse.easyminer.data.impl.parser.LazyByteBufferInputStream
import org.scalatest.{FlatSpec, Matchers}
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
 * Created by propan on 14. 8. 2015.
 */
class AsyncWriterReaderSpec extends FlatSpec with Matchers {

  val initTime = System.currentTimeMillis()

  val logger = LoggerFactory.getLogger("cz.vse.easyminer.data.AsyncWriterReaderSpec")

  "LazyByteBufferInputStream" should "return false if it is full and be able to read sent data" in {
    val lbbis = new LazyByteBufferInputStream(1000, 5 seconds)
    try {
      val bytesSent = Stream.continually(lbbis.write(Array.fill[Byte](100)(1))).takeWhile(_ == true).foldLeft(0) { (sum, _) =>
        sum + 100
      }
      lbbis.finish()
      val byteRead = Stream.continually(lbbis.read()).takeWhile(_ != -1).foldLeft(0) { (sum, _) =>
        sum + 1
      }
      bytesSent shouldBe byteRead
    } finally {
      lbbis.close()
    }
  }

  it should "work asynchronously" in {
    val lbbis = new LazyByteBufferInputStream(1000, 5 seconds)
    val future = Future {
      try {
        val timeBuffer = ListBuffer.empty[Long]
        Stream.continually(lbbis.read()).takeWhile(_ != -1).foldLeft(new StringBuilder) { (line, byte) =>
          if (byte == '\n') {
            timeBuffer.size match {
              case 0 => line.toString() shouldBe "sent line 1"
              case 1 => line.toString() shouldBe "sent line 2"
              case 2 => line.toString() shouldBe "sent line 3"
              case 3 => line.toString() shouldBe "sent line 4"
              case _ =>
            }
            timeBuffer += System.currentTimeMillis()
            line.clear()
            line
          } else {
            line.append(byte.toChar)
          }
        }
        timeBuffer
      } finally {
        lbbis.close()
      }
    }
    var writingTime = 0L
    try {
      Thread.sleep(1000)
      lbbis.write("sent line 1\n".getBytes)
      Thread.sleep(1000)
      lbbis.write("sent line 2\n".getBytes)
      Thread.sleep(1000)
      lbbis.write("sent line 3\n".getBytes)
      writingTime = System.currentTimeMillis()
      Thread.sleep(1000)
      lbbis.write("sent line 4\n".getBytes)
      Thread.sleep(1000)
    } finally {
      lbbis.finish()
    }
    Await.result(future, 5 second).min should be < writingTime
  }

  it should "throw timeout exception during long reading" in {
    val lbbis = new LazyByteBufferInputStream(1000, 3 seconds)
    intercept[TimeoutException] {
      lbbis.read()
    }
  }

  it should "release buffer during reading" in {
    val lbbis = new LazyByteBufferInputStream(1000, 3 seconds)
    try {
      lbbis.write(Array.fill[Byte](1000)(1)) shouldBe true
      lbbis.write(Array.fill[Byte](1)(1)) shouldBe false
      Stream.continually(lbbis.read()).take(1000).sum shouldBe 1000
      lbbis.write(Array.fill[Byte](1)(1)) shouldBe true
      lbbis.write(Array.fill[Byte](99)(1)) shouldBe true
      lbbis.write(Array.fill[Byte](900)(1)) shouldBe true
      lbbis.write(Array.fill[Byte](1)(1)) shouldBe false
      lbbis.read() shouldBe 1
      lbbis.write(Array.fill[Byte](1)(1)) shouldBe true
      lbbis.write(Array.fill[Byte](1)(1)) shouldBe false
    } finally {
      lbbis.close()
    }
  }



}
