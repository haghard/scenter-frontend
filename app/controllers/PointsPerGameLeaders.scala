package controllers

import scala.util.Try
import scala.concurrent.Future
import akka.actor.ActorSystem

import javax.inject.{Inject, Singleton}

import frontend._
import jp.t2v.lab.play2.auth.AuthElement
import play.api.libs.json.JsArray
import play.api.libs.ws.WSClient
import play.api.mvc.{Flash, Result, Controller}

object PointsPerGameLeaders {
  case class PtsLeadersElement(games: Int, player: String, team: String, pic: String, pts: Double)
}

@Singleton
class PointsPerGameLeaders @Inject()(val conf: play.api.Configuration,
                                     val ws: WSClient, val system: ActorSystem) extends Controller
  with AuthElement with AuthorizationConfig with GatewaySupport {
  import PointsPerGameLeaders._

  override val key = "url.pts-leaders"

  override val log = akka.event.slf4j.Logger("pts-leaders")

  def gateway(stage: String, user: Account): Future[Seq[PtsLeadersElement]] = {
    val ptsLeadersUrl = getUrl(key, stage)
    log.info(s"${user.login} -> $ptsLeadersUrl")
    ws.url(ptsLeadersUrl).withHeaders(authHeader -> user.token).get().flatMap { response =>
      response.status match {
        case OK => Future {
          (response.json \ "view").as[JsArray].value.map { item =>
            PtsLeadersElement(item.\("games").as[Int], item.\("player").as[String],
              item.\("team").as[String], s"$picPref${item.\("team").as[String]}.gif", item.\("pts").as[Double])
          }
        }
        case FORBIDDEN =>
          log.info("pts-leaders: refresh-token")
          refreshGatewayToken[PtsLeadersElement](user, stage, gateway)
        case badCode => Future.successful(Seq.empty)
      }
    }
  }

  def fetch(stage: String, user: Account): Future[Result] = {
    val ptsLeadersUrl = getUrl(key, stage)
    log.info(s"${user.login} -> $ptsLeadersUrl")
    ws.url(ptsLeadersUrl).withHeaders(authHeader -> user.token).get().flatMap { response =>
      response.status match {
        case OK =>
          Future.successful(Ok(views.html.leaders.pts(
            Try {
              (response.json \ "view").as[JsArray].value.map { item =>
                PtsLeadersElement(item.\("games").as[Int], item.\("player").as[String],
                  item.\("team").as[String], s"$picPref${item.\("team").as[String]}.gif",
                  item.\("pts").as[Double])
              }
            }.getOrElse(Seq.empty))
          ))
        case FORBIDDEN => refreshToken(user, ptsLeadersUrl, fetch)
        case badStatus =>
          //TODO: error handling on a page
          Flash(Map("pts.error.message" -> s"response code: $badStatus"))
          Future.successful(Ok(views.html.leaders.pts(Nil)))
      }
    }
  }

  def index(stage: String) = AsyncStack(AuthorityKey -> RegularUser) { request =>
    fetch(stage, loggedIn(request))
  }
}