package controllers

import javax.inject._
import akka.actor.ActorSystem
import play.api.libs.ws.WSClient
import play.api.mvc._

@Singleton class AuthController @Inject()(val ws: WSClient, val conf: play.api.Configuration,
                                          val system: ActorSystem) extends Controller {
  def index(provider: String) = Action {
    provider match {
      case "twitter" => Redirect(conf.getString("url.twitter-login").get)
      case "github" => Redirect(conf.getString("url.github-login").get)
    }
  }
}