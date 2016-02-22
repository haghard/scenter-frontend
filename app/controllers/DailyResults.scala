package controllers


import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import frontend.{Account, AuthorizationConfig}
import jp.t2v.lab.play2.auth.AuthElement
import play.api.libs.json.JsArray
import play.api.libs.ws.WSClient
import play.api.mvc.Controller

import scala.concurrent.Future

/*
"arena": "Time Warner Cable Arena | Charlotte, NC",
"lineup": "  bos @ cha ",
"score": "26-22-24-19:91 - 24-18-33-19:94",
"time": "2013-02-10"
*/

case class DailyResult(arena: String, guestTeam: String, homeTeam: String, guestScore: String, homeScore:String,
                       guestScoreLine:String, homeScoreLine:String, date: String)

@Singleton class DailyResults @Inject() (val conf: play.api.Configuration,
                              val ws: WSClient, val system: ActorSystem) extends Controller with AuthElement
  with AuthorizationConfig with GatewaySupport {

  override val key = "url.daily-results"

  override val log = akka.event.slf4j.Logger("daily-results")

  def gateway(stage: String, user: Account): Future[Seq[DailyResult]] = {
    val url = getUrl(key, stage)
    log.info(s"${user.login} -> $url")
    ws.url(url).withHeaders(authHeader -> user.token).get().flatMap { response =>
      response.status match {
        case OK => Future {
          (response.json \ "view").as[JsArray].value.map { item =>
            val teams = item.\("lineup").as[String].trim.split("@")
            val score = item.\("score").as[String].trim.split(" - ")
            val guestT = score(0).split(":")
            val guestFinalScore = guestT(1)
            val guestScoreLine = guestT(0)
            val homeT = score(1).split(":")
            val homeFinalScore = homeT(1)
            val homeScoreLine = homeT(0)
            DailyResult(item.\("arena").as[String], s"$picPref${teams(0).trim}.gif", s"$picPref${teams(1).trim}.gif",
              guestFinalScore, homeFinalScore, guestScoreLine, homeScoreLine,
              item.\("time").as[String])
          }
        }
        case FORBIDDEN => refreshGatewayToken[DailyResult](user, stage, gateway)
        case badCode => Future.successful(Seq.empty)
      }
    }
  }
}
