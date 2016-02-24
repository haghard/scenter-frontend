package controllers

import akka.actor.ActorSystem
import frontend.AccountModel
import play.api.cache.CacheApi
import play.api.libs.json.Json
import scala.concurrent.Future
import javax.inject.{Inject, Singleton}
import com.github.scribejava.core.model.Verb
import controllers.oauth.Oauth
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}
import scalaz._
import Scalaz._

@Singleton class Twitter @Inject()(val ws: WSClient, val conf: play.api.Configuration,
                                   cache: CacheApi, val system: ActorSystem) extends Controller {
  val log = akka.event.slf4j.Logger("oAuth-twitter")
  implicit val ex = system.dispatchers.lookup("akka.stream-dispatcher")

  val oauth = Oauth[com.github.scribejava.apis.TwitterApi].fromConfig(
    conf.getString("twitter.consumer_key").get,
    conf.getString("twitter.consumer_secret").get)

  def callback = Action.async { implicit req =>
    val oauthToken = for {
      oauthToken <- req.getQueryString(oauth.header) \/> (s"${oauth.header} is absent")
      oauthVerifier <- req.getQueryString(oauth.headerV) \/> (s"${oauth.headerV} is absent")
    } yield new com.github.scribejava.core.model.Token(oauthToken, oauthVerifier)

    oauthToken.fold({ _ => Future.successful(Redirect(routes.SportCenter.login(false))) }, {
      token: com.github.scribejava.core.model.Token =>
        Future {
          val service = oauth.oAuthService.build(oauth.instance)
          val verifier = new com.github.scribejava.core.model.Verifier(token.getSecret)
          val accessToken = service.getAccessToken(token, verifier)
          val oAuthRequest = new com.github.scribejava.core.model.OAuthRequest(Verb.GET, oauth.protectedUrl, service)
          service.signRequest(accessToken, oAuthRequest)
          val twitterResponse = oAuthRequest.send()
          if (twitterResponse.getCode == 200) (Json.parse(twitterResponse.getBody) \ ("name")).as[String] else "unknown-auth-twitter"
        }.flatMap { login =>
          log.info(s"Login from twitter: $login")
          AccountModel.insertOauthUser(login, frontend.DefaultTwitterPassword, frontend.DefaultTwitterUser).map { id =>
            //http://stackoverflow.com/questions/13068523/playframework-how-to-redirect-to-a-post-call-inside-controller-action-method
            /*
            more secured way with cache
            import scala.concurrent.duration._
            cache.set(frontend.DefaultTwitterUser, s"$login,${frontend.DefaultTwitterPassword}", 5 seconds)
            Redirect(routes.SportCenter.authenticateOauthwithCache)
            */
            //unsecured way: play params
            Redirect(routes.SportCenter.authenticateOauth(login, frontend.DefaultTwitterPassword))
          }
        }
    })
  }
}