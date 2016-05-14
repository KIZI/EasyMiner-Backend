package cz.vse.easyminer.data.impl.parser

import java.io.InputStream
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import cz.vse.easyminer.data.BufferedWriter

import scala.annotation.tailrec
import scala.concurrent.duration.Duration

/**
 * Created by propan on 3. 8. 2015.
 * This class serves as a byte array writer and an input stream reader simultaneously.
 * Input contructor parameters are limit and timeout.
 *
 * Limit: Maximal buffer size in bytes. If the write method is called and the buffer is full, it return false and data will not be written.
 * You must send it again after reading saved data.
 * Timeout: Maximal waiting time for input data. If there are no data in the buffer and the read method is called,
 * the timer will start to countdown. As soon as the waiting time is greater than the timeout duration, the read method returns -1
 * and the input stream will be closed.
 *
 * There is some limitation within using of this class: You should not write larger data than is the buffer limit.
 * The timeout duration is always rounded to the second unit. The minimal duration time is one second.
 *
 * This implementation is not absolutely thread safe. It is assumed that it will be used in two threads: one thread for writing and second for reading.
 * So reading and writing can proceed concurrently and it is thread safe if you use just two threads.
 *
 * It is important to call the finish method after writing completion.
 * After this, if the buffer is empty the read method will return -1 and the input stream reader will be closed.
 */
class LazyByteBufferInputStream(limit: Int, timeout: Duration) extends InputStream with BufferedWriter {

  /**
   * Thread safe buffer
   */
  private val safeBuffer = collection.mutable.Queue.empty[Array[Byte]]
  /**
   * Thread safe finish flag
   */
  private val isFinished = new AtomicBoolean(false)
  /**
   * This buffer is not thread safe, but faster. It is used for fast reading.
   */
  private var unsafeBuffer: Array[Byte] = Array()
  /**
   * Pointer to the unsafe buffer current position.
   */
  private var currentIndex = 0
  /**
   * Current size of the buffeer (safe buffer + unsafe buffer)
   */
  private var size = 0
  /**
   * Current waiting time in seconds
   */
  private var waitingTime = 0

  /**
   * Write input data chunk to the buffer and forward it to the input stream
   *
   * @param bytes Input data chunk
   * @return true - if data were saved to the buffer
   *         false - if the buffer is full (input chunk is not saved)
   */
  def write(bytes: Array[Byte]): Boolean = safeBuffer.synchronized {
    val newSize = size + bytes.length
    if (newSize > limit) {
      false
    } else if (isFinished.get()) {
      true
    } else {
      safeBuffer += bytes
      size = newSize
      true
    }
  }

  /**
   * Finish the writing process. After calling this method, the read method returns -1 if the buffer is empty
   */
  def finish(): Unit = isFinished.compareAndSet(false, true)

  /**
   * Call the finish method and clear the buffer.
   * This method is not absolutely thread safe. It should be called only from the reading thread.
   */
  override def close(): Unit = {
    finish()
    safeBuffer.synchronized {
      safeBuffer.clear()
      unsafeBuffer = Array()
    }
  }

  /**
   * This method should be called only from the reading thread (only one reading thread is expected).
   * It reads data from the buffer byte by byte. It is blocking, if there are no data in the buffer it is waiting.
   * After timeout reaching TimeoutException will be thrown.
   *
   * @return This returns the current byte or -1 if end of stream.
   */
  @tailrec
  final def read(): Int = if (unsafeBuffer.nonEmpty) {
    //fast reading
    //it is not thread safe, only one thread should call this method.
    //reading from array
    val byte = unsafeBuffer(currentIndex) & 0xff
    currentIndex = currentIndex + 1
    if (currentIndex == unsafeBuffer.length) safeBuffer.synchronized {
      //if the array is read, it frees memory in the buffer
      size = size - unsafeBuffer.length
      unsafeBuffer = Array()
    }
    byte
  } else if (safeBuffer.nonEmpty) {
    //array is empty and the safe buffer is not empty
    //move data from the peak of the thread safe buffer to the unsafe array
    //set start pointer, clear waiting time and call fast reading
    safeBuffer.synchronized {
      unsafeBuffer = safeBuffer.dequeue()
    }
    waitingTime = 0
    currentIndex = 0
    read()
  } else if (isFinished.get()) {
    //writing has been finished and all buffers are empty
    //end of stream reading
    -1
  } else if (waitingTime > timeout.toSeconds) {
    //waiting time reached the timeout duration, exception
    throw new TimeoutException("Timeout during input stream reading.")
  } else {
    //no data, wait one second and try to read it again.
    Thread.sleep(1000)
    waitingTime = waitingTime + 1
    read()
  }

}
