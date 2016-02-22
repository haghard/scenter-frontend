package controllers

import javax.inject._

import akka.actor.ActorSystem
import com.github.scribejava.core.model.Verb
import frontend.AuthorizationConfig
import jp.t2v.lab.play2.auth.AuthElement
import org.slf4j.Logger
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.Future

@Singleton class TwitterAuth @Inject()(val ws: WSClient,
                                       val conf: play.api.Configuration,
                                       val system: ActorSystem) extends Controller with AuthElement
  with AuthorizationConfig with GatewaySupport with MaterializerSupport {

  val header = "oauth_token"
  val headerV = "oauth_verifier"

  val twitter = new com.github.scribejava.core.builder.ServiceBuilder()
    .provider(classOf[com.github.scribejava.apis.TwitterApi])
    .apiKey(conf.getString("twitter.consumer_key").get)
    .apiSecret(conf.getString("twitter.consumer_secret").get)

  val protectedUri = "https://api.twitter.com/1.1/account/verify_credentials.json"

  override val key = "url.twitter-login"

  override val log: Logger = akka.event.slf4j.Logger("twitter-login")

  def callback = Action.async { implicit req =>
    import spray.json._

    import scalaz._
    import Scalaz._

    val validation = for {
      oauthToken <- req.getQueryString(header) \/> (s"$header is absent")
      oauthVerifier <- req.getQueryString(headerV) \/> (s"$headerV is absent")
    } yield new com.github.scribejava.core.model.Token(oauthToken, oauthVerifier)

    validation.fold({ _ => Future.successful(Redirect(routes.SportCenter.login(false))) }, {
      token: com.github.scribejava.core.model.Token =>
        Future {
          val service = twitter.build()
          val verifier = new com.github.scribejava.core.model.Verifier(token.getSecret)
          val accessToken = service.getAccessToken(token, verifier)
          val oAuthRequest = new com.github.scribejava.core.model.OAuthRequest(Verb.GET, protectedUri, service)
          service.signRequest(accessToken, oAuthRequest)
          val twitterResponse = oAuthRequest.send()
          //log.info(twitterResponse.getBody.parseJson.prettyPrint)
          if (twitterResponse.getCode == 200) {
            val json = twitterResponse.getBody.parseJson.asJsObject
            json.getFields("name").head.toString().replace("\"", "")
          } else "unknown-auth-twitter"
        }.flatMap { login =>
          //TODO: handle twitter user correctly
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

  def index = Action.async {
    Future.successful(Redirect(conf.getString(key).get))
  }
}