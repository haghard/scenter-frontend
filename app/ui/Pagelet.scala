package ui

import play.twirl.api.Html
import scala.concurrent.{ExecutionContext, Future}
import play.api.http.HeaderNames
import play.api.mvc.{Cookie, Cookies, Result}

/**
 * Helpers for working with pieces of pages.
 */
object Pagelet {

  /**
   * Read the body of a Result as Html.
   *
   */
  def readBody(result: Result)(implicit mat: akka.stream.ActorMaterializer): Future[Html] =
    result.body.consumeData.map(bytes => Html(bytes.utf8String))(mat.executionContext)

  /**
   * Merge all the cookies set in the given results into a single sequence
   */
  def mergeCookies(results: Result*): Seq[Cookie] = {
    results.flatMap(result => result.header.headers.get(HeaderNames.SET_COOKIE)
      .map(Cookies.decodeSetCookieHeader).getOrElse(Seq.empty))
  }

  /**
   * Wrap the given Html in a script tag that will inject the Html into the DOM node with the given id
   *
   * @param html
   * @param id
   * @return
   */
  def render(html: Html, id: String): Html = views.html.ui.pagelet(html, id)

  /**
   * Wrap the given Html in a script tag that will inject the Html into the DOM node with the given id. Returns an
   * HtmlStream that can be used in a .scala.stream template.
   *
   */

  def renderStream(html: Html, id: String): HtmlStream = {
    HtmlStream(render(html, id))
  }


  /**
   * Wrap the given Html in a script tag that will inject the Html into the DOM node with the given id. Returns an
   * HtmlStream that can be used in a .scala.stream template.
   *
   */
  def renderStream(htmlFuture: Future[Html], id: String)(implicit ex: ExecutionContext): HtmlStream = {
    HtmlStream.flatten(htmlFuture.map(html => renderStream(html, id)))
  }
}