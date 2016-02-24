package controllers

import javax.inject.{Singleton, Inject}
import akka.actor.ActorSystem
import com.github.scribejava.core.model.{OAuthAsyncRequestCallback, Verb}
import controllers.oauth.Oauth
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future

@Singleton
class Github @Inject()(val ws: WSClient, val conf: play.api.Configuration,
                       val system: ActorSystem) extends Controller {

  val log = akka.event.slf4j.Logger("oAuth-github")
  implicit val ex = system.dispatchers.lookup("akka.stream-dispatcher")

  val oauth = Oauth[com.github.scribejava.apis.GitHubApi].fromConfig(
    conf.getString("github.consumer_key").get,
    conf.getString("github.consumer_secret").get)

  def callback() = Action.async { implicit req =>
    import scalaz._
    import Scalaz._

    val headers = for {
      oauthToken <- req.getQueryString(oauth.header) \/> (s"${oauth.header} is absent")
      oauthVerifier <- req.getQueryString(oauth.headerV) \/> (s"${oauth.headerV} is absent")
    } yield new com.github.scribejava.core.model.Token(oauthToken, oauthVerifier)

    headers.fold({ _ => Future.successful(Redirect(routes.SportCenter.login(false))) }, {
      token: com.github.scribejava.core.model.Token =>
        Future {
          val service = oauth.oAuthService.build(oauth.instance)
          val verifier = new com.github.scribejava.core.model.Verifier(token.getSecret)

          //new OAuthAsyncRequestCallback[com.github.scribejava.core.model.Token]
          val accessToken = service.getAccessToken(verifier)
          val oAuthRequest = new com.github.scribejava.core.model.OAuthRequest(Verb.GET, oauth.protectedUrl, service)
          service.signRequest(accessToken, oAuthRequest)
          val twitterResponse = oAuthRequest.send()
          if (twitterResponse.getCode == 200) {
            //val body = twitterResponse.getBody.parseJson.asJsObject
            //body.getFields("name").head.toString().replace("\"", "")
            ""
          } else "unknown-auth-github"
        }.flatMap { login =>
          //TODO: handle github user correctly
          log.info(s"login with github user $login")
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