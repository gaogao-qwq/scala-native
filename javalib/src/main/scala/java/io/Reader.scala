// Ported from Scala.js, commit: 7d7a621, dated 2022-03-07
// See SN Repository git history for Scala Native additions.

package java.io

import java.nio.CharBuffer
import java.util.Objects

import scala.annotation.tailrec

abstract class Reader() extends Readable with Closeable {
  protected var lock: Object = this

  protected def this(lock: Object) = {
    this()
    if (lock eq null)
      throw new NullPointerException()
    this.lock = lock
  }

  def read(target: CharBuffer): Int = {
    Objects.requireNonNull(target, "target") // JVM uses helpful null msg

    if (!target.hasRemaining()) 0
    else if (target.hasArray()) {
      val charsRead = read(
        target.array(),
        target.position() + target.arrayOffset(),
        target.remaining()
      )
      if (charsRead != -1)
        target.position(target.position() + charsRead)
      charsRead
    } else {
      val buf = new Array[Char](target.remaining())
      val charsRead = read(buf)
      if (charsRead != -1)
        target.put(buf, 0, charsRead)
      charsRead
    }
  }

  def read(): Int = {
    val buf = new Array[Char](1)
    if (read(buf) == -1) -1
    else buf(0).toInt
  }

  def read(cbuf: Array[Char]): Int =
    read(cbuf, 0, cbuf.length)

  def read(cbuf: Array[Char], off: Int, len: Int): Int

  def skip(n: Long): Long = {
    if (n < 0)
      throw new IllegalArgumentException("Cannot skip negative amount")

    val buffer = new Array[Char](8192)
    @tailrec
    def loop(m: Long, lastSkipped: Long): Long = {
      if (m <= 0) {
        lastSkipped
      } else {
        val mMin = Math.min(m, 8192).toInt
        val skipped = read(buffer, 0, mMin)
        if (skipped < 0) {
          lastSkipped
        } else {
          val totalSkipped = lastSkipped + skipped
          loop(m - mMin, totalSkipped)
        }
      }
    }
    loop(n, 0)
  }

  def ready(): Boolean = false

  def markSupported(): Boolean = false

  def mark(readAheadLimit: Int): Unit =
    throw new IOException("Mark not supported")

  def reset(): Unit =
    throw new IOException("Reset not supported")

  def close(): Unit

  // Since: Java 10
  def transferTo(out: Writer): Long = {
    val buffer = new Array[Char](4096)

    @tailrec
    def loop(nRead: Long): Long = {
      val n = this.read(buffer)
      if (n == -1) {
        nRead
      } else {
        out.write(buffer, 0, n)
        loop(nRead + n)
      }
    }

    loop(0)
  }
}

object Reader {

  /** @since JDK 11 */
  def nullReader(): Reader = {
    new Reader() {
      private var closed = false

      private def ensureOpen(): Unit = {
        if (closed)
          throw new IOException("Stream closed")
      }

      def close(): Unit =
        closed = true

      def read(cbuf: Array[Char], off: Int, len: Int): Int = {
        Objects.requireNonNull(
          cbuf,
          "Cannot read the array length because \"cbuf\" is null"
        )
        Objects.checkFromIndexSize(off, len, cbuf.length)

        ensureOpen()

        if (len == 0) 0 else -1 // zero is JVM corner case.
      }

      override def ready(): Boolean = {
        ensureOpen()
        super.ready()
      }

      override def skip(n: Long): Long = {
        ensureOpen()
        super.skip(n)
      }

      override def transferTo(out: Writer): Long = {
        ensureOpen()
        super.transferTo(out)
      }
    }
  }
}
