package ui

import play.api.libs.iteratee.{Enumeratee, Enumerator}
import scala.collection.immutable.Seq
import play.twirl.api.{Format, HtmlFormat, Html, Appendable}
import scala.concurrent.Future
import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits._

/**
 * A custom Appendable that lets us have .scala.stream templates instead of .scala.html. These templates can mix Html
 * markup with Enumerators that contain Html markup. We add this class as a custom template type in build.sbt.
 *
 * @param enumerator
 */
case class HtmlStream(enumerator: Enumerator[Html]) extends Appendable[HtmlStream] {
  def +=(other: HtmlStream): HtmlStream = ++(other)

  def ++(other: HtmlStream): HtmlStream = HtmlStream(enumerator.andThen(other.enumerator))
}

/**
 * Companion object for EnumHtmlStream that contains convenient factory and composition methods.
 */
object HtmlStream {

  /**
   * Create an HtmlStream from a String
   *
   * @param text
   * @return
   */
  def apply(text: String): HtmlStream = {
    apply(Html(text))
  }

  /**
   * Create an HtmlStream from Html
   *
   */
  def apply(html: Html): HtmlStream = {
    HtmlStream(Enumerator(html))
  }

  /**
   * Create an HtmlStream from a Future that will eventually contain Html
   *
   */
  def fromFuture(eventuallyHtml: Future[Html]): HtmlStream = {
    flatten(eventuallyHtml.map(apply))
  }

  def fromResult(result: Result)(implicit mat: akka.stream.Materializer): HtmlStream = {
    flatten(result.body.dataStream.map(bytes => HtmlStream(Html(bytes.utf8String))).runReduce(_ ++ _))
  }

  /**
   * Create an HtmlStream from a the body of a Future[SimpleResult].
   *
   */
  def fromResult(result: Future[Result])(implicit mat: akka.stream.Materializer): HtmlStream = {
    flatten(result.map(fromResult))
  }

  /**
   * Interleave multiple HtmlStreams together. Interleaving is done based on whichever HtmlStream next has input ready,
   * if multiple have input ready, the order is undefined.
   *
   */
  def interleave(streams: HtmlStream*): HtmlStream = {
    HtmlStream(Enumerator.interleave(streams.map(_.enumerator)))
  }

  /**
   * Create an HtmlStream from a Future that will eventually contain an HtmlStream.
   *
   */
  def flatten(eventuallyStream: Future[HtmlStream]): HtmlStream = {
    HtmlStream(Enumerator.flatten(eventuallyStream.map(_.enumerator)))
  }
}

/**
 * A custom Format that lets us have .scala.stream templates instead of .scala.html.
 * These templates can mix Html markup with Enumerators that contain Html markup.
 */
object HtmlStreamFormat extends Format[HtmlStream] {

  def raw(text: String): HtmlStream =
    HtmlStream(text)

  def escape(text: String): HtmlStream =
    raw(HtmlFormat.escape(text).body)

  override def empty: HtmlStream = HtmlStream("")

  override def fill(elements: Seq[HtmlStream]): HtmlStream =
    elements.reduce(_ ++ _)
}


/**
 * Useful implicits when working with HtmlStreams
 */
object HtmlStreamImplicits {

  // Implicit conversion so HtmlStream can be passed directly to Ok.feed and Ok.chunked
  implicit def toEnumerator(stream: HtmlStream): Enumerator[Html] = {
    // Skip empty chunks, as these mean EOF in chunked encoding
    stream.enumerator.through(Enumeratee.filter(!_.body.isEmpty))
  }
}