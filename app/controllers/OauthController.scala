package controllers

import java.net.URLEncoder
import javax.inject._
import akka.actor.ActorSystem
import play.api.http.HeaderNames._
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.Future

@Singleton
class OauthController @Inject()(val ws: WSClient, val conf: play.api.Configuration,
                                val system: ActorSystem) extends Controller {

  val log = akka.event.slf4j.Logger("oauth-controller")
  val FHeader = "X-Forwarded-For"
  val inetAddress = s"${conf.getString("http.ip").get}:${conf.getString("http.port").get}"

  def fullUrl(url: String, queryString: Map[String, Seq[String]]) = url + Option(queryString).filterNot(_.isEmpty).map { params =>
    (if (url.contains("?")) "&" else "?") + params.toSeq.flatMap { pair =>
      pair._2.map(value => (pair._1 + "=" + URLEncoder.encode(value, "utf-8")))
    }.mkString("&")
  }.getOrElse("")

  def index(provider: String) = Action { implicit request =>
    //request.headers.add(FHeader -> inetAddress)
    provider match {
      case "twitter" =>
        //log.info(s"$FHeader: $inetAddress")
        //Future { Redirect(conf.getString("url.twitter-login").get) }
        Redirect(conf.getString("url.twitter-login").get).withHeaders("X-Forwarded-For"-> inetAddress)
      case "github" => Redirect(conf.getString("url.github-login").get).withHeaders("X-Forwarded-For" -> inetAddress)
    }
  }
}