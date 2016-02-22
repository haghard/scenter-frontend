package controllers

import akka.actor.ActorSystem
import play.api.libs.ws.WSClient
import play.api.mvc.Result
import frontend.{AccountModel, Account}
import scala.concurrent.{Future, ExecutionContext}

trait GatewaySupport {
  mixin: {
    def conf: play.api.Configuration
    def loginUrl: String
    def ws: WSClient
    def system: ActorSystem
  }  =>

  lazy val picPref = "/assets/bootstrap/images/"
  lazy val authHeader = conf.getString("http-session.auth").get
  lazy val backendAuthHeader = conf.getString("http-session.auth-backend").get

  def log: org.slf4j.Logger

  implicit val ex = system.dispatchers.lookup("akka.stream-dispatcher")

  def key: String

  def getUrl(key: String, stage: String) = conf.getString(key).get + stage

  def refreshGatewayToken[T](user: Account, stage: String, f: (String, Account) => Future[Seq[T]])
                            (implicit ex: ExecutionContext): Future[Seq[T]] = {
    AccountModel.refreshToken(loginUrl, user, backendAuthHeader, ws).flatMap { tokenCtx =>
      tokenCtx.fold(Future.failed[Seq[T]](new Exception("Refresh-token action has failed"))) { account =>
        AccountModel.updateToken(user.login, user.password, account.token)
          .flatMap  { count => f(stage, user.copy(token = account.token))  }
      }
    }
  }

  def refreshToken(user: Account, url: String, f: (String, Account) => Future[Result])
                  (implicit ex: ExecutionContext): Future[Result] = {
    AccountModel.refreshToken(loginUrl, user, backendAuthHeader, ws).flatMap { tokenCtx =>
      tokenCtx.fold(Future.failed[Result](new Exception("Refresh-token action has failed"))) { account =>
        AccountModel.updateToken(user.login, user.password, account.token)
          .flatMap  { count => f(url, user.copy(token = account.token))  }
      }
    }
  }
}
