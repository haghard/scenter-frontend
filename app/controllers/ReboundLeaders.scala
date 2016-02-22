package controllers

import javax.inject.{Singleton, Inject}

import akka.actor.ActorSystem
import frontend.{Account, RegularUser, AuthorizationConfig}
import jp.t2v.lab.play2.auth.AuthElement
import play.api.libs.json.JsArray
import play.api.libs.ws.WSClient
import play.api.mvc.{Flash, Result, Controller}
import scala.concurrent.Future
import scala.util.Try

object ReboundLeaders {
  case class RebLeadersElement(games: Int, player: String, team: String, pic: String, results: String)
}

@Singleton class ReboundLeaders @Inject()(val conf: play.api.Configuration,
                               val ws: WSClient, val system: ActorSystem) extends Controller
  with AuthElement with AuthorizationConfig with GatewaySupport {
  import ReboundLeaders._

  override val key = "url.reb-leaders"

  override val log = akka.event.slf4j.Logger("reb-leaders")

  def gateway(stage: String, user: Account): Future[Seq[RebLeadersElement]] = {
    val rebLeadersUrl = getUrl(key, stage)
    log.info(s"${user.login} -> $rebLeadersUrl")
    ws.url(rebLeadersUrl).withHeaders(authHeader -> user.token).get().flatMap { response =>
      response.status match {
        case OK => Future {
          (response.json \ "view").as[JsArray].value.map { item =>
            RebLeadersElement(item.\("games").as[Int], item.\("player").as[String], item.\("team").as[String],
              s"$picPref/${item.\("team").as[String]}.gif",
              s"def/off/total: ${item.\("defensive").as[Double]}/${item.\("offensive").as[Double]}/${item.\("total").as[Double]}")
          }
        }
        case FORBIDDEN =>
          log.info("reb-leaders: refresh-token")
          refreshGatewayToken[RebLeadersElement](user, stage, gateway)
        case badStatus =>
          Future.successful(Seq.empty)
      }
    }
  }

  def fetch(stage: String, user: Account): Future[Result] = {
    val rebLeadersUrl = getUrl(key, stage)
    log.info(s"${user.login} -> $rebLeadersUrl")
    ws.url(rebLeadersUrl).withHeaders(authHeader -> user.token).get().flatMap { response =>
      response.status match {
        case OK =>
          Future.successful(Ok(views.html.leaders.reb(
            Try {
              (response.json \ "view").as[JsArray].value.map { item =>
                RebLeadersElement(item.\("games").as[Int], item.\("player").as[String], item.\("team").as[String],
                  s"$picPref${item.\("team").as[String]}.gif",
                  s"def/off/total: ${item.\("defensive").as[Double]}/${item.\("offensive").as[Double]}/${item.\("total").as[Double]}")
              }
            }.getOrElse(Seq.empty))
          ))
        case FORBIDDEN => refreshToken(user, rebLeadersUrl, fetch)
        case badStatus =>
          //TODO: error handling on a page
          Flash(Map("reb.error.message" -> s"response code: $badStatus"))
          Future.successful(Ok(views.html.leaders.reb(Nil)))
      }
    }
  }

  def index(stage: String) = AsyncStack(AuthorityKey -> RegularUser) { request =>
    val user = loggedIn(request)
    fetch(stage, user)
  }
}