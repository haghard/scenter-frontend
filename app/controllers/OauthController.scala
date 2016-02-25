package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class OauthController @Inject()(val conf: play.api.Configuration) extends Controller {
  def index(provider: String) = Action {
    provider match {
      case "twitter" => Redirect(conf.getString("url.twitter-login").get)
      case "github" => Redirect(conf.getString("url.github-login").get)
    }
  }
}