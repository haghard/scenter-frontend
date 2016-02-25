package controllers

import java.net.URLEncoder
import javax.inject._
import akka.actor.ActorSystem
import play.api.libs.ws.WSClient
import play.api.mvc._

@Singleton
class OauthController @Inject()(val ws: WSClient, val conf: play.api.Configuration,
                                val system: ActorSystem) extends Controller {

  //val log = akka.event.slf4j.Logger("oauth-controller")
  //val FHeader = "X-Forwarded-For"
  //val inetAddress = s"${conf.getString("http.ip").get}:${conf.getString("http.port").get}"

  def index(provider: String) = Action {
    provider match {
      case "twitter" => Redirect(conf.getString("url.twitter-login").get)
      case "github" => Redirect(conf.getString("url.github-login").get)
    }
  }
}