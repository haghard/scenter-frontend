package controllers

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import com.github.scribejava.core.model.Verb
import controllers.oauth.Oauth
import frontend.AuthorizationConfig
import jp.t2v.lab.play2.auth.AuthElement
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future

@Singleton class Twitter @Inject()(val ws: WSClient, val conf: play.api.Configuration,
                                   val system: ActorSystem) extends Controller
  with AuthElement with AuthorizationConfig {

  val log = akka.event.slf4j.Logger("oAuth-twitter")

  val oauth = Oauth[com.github.scribejava.apis.TwitterApi].fromConfig(
    conf.getString("twitter.consumer_key").get,
    conf.getString("twitter.consumer_secret").get
  )

  implicit val ex = system.dispatchers.lookup("akka.stream-dispatcher")

  def callback = Action.async { implicit req =>
    import spray.json._

    import scalaz._
    import Scalaz._

    val headers = for {
      oauthToken <- req.getQueryString(oauth.header) \/> (s"${oauth.header} is absent")
      oauthVerifier <- req.getQueryString(oauth.headerV) \/> (s"${oauth.headerV} is absent")
    } yield new com.github.scribejava.core.model.Token(oauthToken, oauthVerifier)

    headers.fold({ _ => Future.successful(Redirect(routes.SportCenter.login(false))) }, {
      token: com.github.scribejava.core.model.Token =>
        Future {
          val service = oauth.oAuthService.build()
          val verifier = new com.github.scribejava.core.model.Verifier(token.getSecret)
          val accessToken = service.getAccessToken(token, verifier)
          val oAuthRequest = new com.github.scribejava.core.model.OAuthRequest(Verb.GET, oauth.protectedUrl, service)
          service.signRequest(accessToken, oAuthRequest)
          val twitterResponse = oAuthRequest.send()
          if (twitterResponse.getCode == 200) {
            val json = twitterResponse.getBody.parseJson.asJsObject
            json.getFields("name").head.toString().replace("\"", "")
          } else "unknown-auth-twitter"
        }.flatMap { login =>
          //TODO: handle twitter user correctly
          log.info(s"login with twitter user $login")
          Future.successful(Redirect(routes.SportCenter.login(true)))

          /*AccountModel.authenticate(login, "twitter-password") }.map { res =>
            res.fold(Redirect(routes.SportCenter.login(false))) { account =>
              log.info("Account: {}", account.toString)
              Redirect(routes.SportCenter.login(true))
            }
          }*/
        }
    })
  }
}
