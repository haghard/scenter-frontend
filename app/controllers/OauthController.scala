package controllers

import javax.inject._
import akka.actor.ActorSystem
import play.api.libs.ws.WSClient
import play.api.mvc._

@Singleton
class OauthController @Inject()(val ws: WSClient, val conf: play.api.Configuration,
                                val system: ActorSystem) extends Controller {

  val log = akka.event.slf4j.Logger("oauth-controller")
  val inetAddress = s"${conf.getString("http.ip").get}:${conf.getString("http.port").get}"

  def index(provider: String) = Action {
    provider match {
      case "twitter" =>
        log.info("X-Forwarded-For: " + inetAddress)
        Redirect(conf.getString("url.twitter-login").get).withHeaders("X-Forwarded-For"-> inetAddress)
      case "github" =>
        Redirect(conf.getString("url.github-login").get).withHeaders("X-Forwarded-For"-> inetAddress)
    }
  }
}