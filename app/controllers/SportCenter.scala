package controllers

import akka.actor.ActorSystem
import frontend.AuthorizationConfig
import play.api.cache.CacheApi
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

//http://www.tzavellas.com/techblog/2015/02/10/action-composition-in-play-framework/
@Singleton
class SportCenter @Inject()(val conf: play.api.Configuration,
                                       cache: CacheApi, system: ActorSystem) extends Controller
  with jp.t2v.lab.play2.auth.LoginLogout with AuthorizationConfig {

  val log: org.slf4j.Logger = akka.event.slf4j.Logger("sport-center")
  implicit val ex = system.dispatchers.lookup("akka.stream-dispatcher")

  import scala.concurrent.duration._
  implicit val timeout = 3 seconds

  def loginForm = Form {
    mapping(
      ("login" -> nonEmptyText),
      ("password" -> nonEmptyText))(frontend.AccountModel.authenticateSync)(_.map(account => (account.login, account.password)))
         .verifying("Invalid login or password", _.isDefined)
  }

  def login(loginError: Boolean) = Action { implicit request =>
    if(loginError) BadRequest(views.html.login.login(authenticationErrorForm("authentication error")))
    else Ok(views.html.login.login(loginForm))
  }

  def logout = Action.async { implicit request =>
    gotoLogoutSucceeded.map(_.flashing(("success", "You've been logged out")))
  }

  def authenticate = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      { formWithErrors =>
        Future.successful(BadRequest(views.html.login.login(authenticationErrorForm("Login or password is missing"))))
      }, { user =>
        gotoLoginSucceeded(s"${user.get.id},${user.get.login},${user.get.password},${user.get.permission},${user.get.token}")
      }
    )
  }

  def authenticateOauth(login: String, password: String) = Action.async { implicit request =>
    frontend.AccountModel.authenticate(login, password).flatMap {
      _.fold(Future.successful(BadRequest(views.html.login.login(authenticationErrorForm(s"Can't find oauth user with $login"))))) { user =>
        gotoLoginSucceeded(s"${user.id},${user.login},${user.password},${user.permission},${user.token}")
      }
    }
  }

  def authenticateOauthThroughCache = Action.async { implicit request =>
    cache.get[String](frontend.DefaultTwitterUser).fold(Future.successful(BadRequest(views.html.login.login(authenticationErrorForm(s"Can't find oauth user in cache"))))) { fields =>
      val authParts = fields.split(",")
      frontend.AccountModel.authenticate(authParts(0), authParts(1)).flatMap {
        _.fold(Future.successful(BadRequest(views.html.login.login(authenticationErrorForm(s"Can't find oauth user with ${authParts(0)}"))))) { user =>
          gotoLoginSucceeded(s"${user.id},${user.login},${user.password},${user.permission},${user.token}")
        }
      }
    }
  }
}