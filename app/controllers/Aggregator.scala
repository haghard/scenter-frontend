package controllers

import play.api.mvc._
import ui.{HtmlStream, Pagelet}
import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.auth.AuthElement
import frontend.{RegularUser, AuthorizationConfig}

@Singleton class Aggregator @Inject()(val conf: play.api.Configuration,
                                      //controller: TaxiController,
                                      rebCnt: ReboundLeaders, ptsCnt: PointsPerGameLeaders, daily: DailyResults,
                                      val system: ActorSystem) extends Controller
  with AuthElement with AuthorizationConfig
  with MaterializerSupport {

  val log: org.slf4j.Logger = akka.event.slf4j.Logger("aggregator")

  def index(stage: String) = StackAction(AuthorityKey -> RegularUser) { implicit request =>
    val user = loggedIn(request)
    log.info(s"aggregator ${user.login} -> ${request.uri}")

    val dailyStream = Pagelet.renderStream(daily.gateway("2016-02-10", user).map(views.html.daily.results(_)), "daily")
    val rebStream = Pagelet.renderStream(rebCnt.gateway(stage, user).map(views.html.leaders.reb(_)), "reb-lead")
    val prsStream = Pagelet.renderStream(ptsCnt.gateway(stage, user).map(views.html.leaders.pts(_)), "pts-lead")

    val body = HtmlStream.interleave(rebStream, prsStream, dailyStream)

    //TODO: use akka Source instead Enumerator

    import ui.HtmlStreamImplicits._
    Ok.chunked(views.stream.aggregatorBody(body))

  /*
    val rebStream = Pagelet.renderStream(rebCnt.fetch("season-15-16", user),"reb-lead")
    val ptsStream = Pagelet.renderStream(ptsCnt.fetch("season-15-16", user), "pts-lead")
    Ok.chunked(toEnumerator(views.html.aggregator.aggregator(HtmlStream.interleave(rebStream, ptsStream))))
    */

    /*
    import scalaz._, Scalaz._
    ((rebCnt.index("season-15-16")(request)).flatMap(Pagelet.readBody(_)) |@|
      (ptsCnt.index("season-15-16")(request)).flatMap(Pagelet.readBody(_))) { (rebBody: HtmlStream, ptsBody: HtmlStream) =>
      Ok(views.html.aggregator.aggregator(rebBody, ptsBody))
    }*/

    /*
    scalaz.Applicative[Future].apply2(
      (rebCnt.index("season-15-16")(request)).flatMap(Pagelet.readBody(_)),
      (ptsCnt.index("season-15-16")(request)).flatMap(Pagelet.readBody(_))) { (rebBody: Html, ptsBody: Html) =>
          Ok(views.html.aggregator.aggregator(rebBody, ptsBody))
    }*/

    /*
    for {
      reb <- rebCnt.index("season-15-16")(request)
      pts <- ptsCnt.index("season-15-16")(request)
      rebBody <- Pagelet.readBody(reb)
      ptsBody <- Pagelet.readBody(pts)
    } yield Ok(views.html.aggregator.aggregator(rebBody, ptsBody)).withCookies(Pagelet.mergeCookies(reb, pts): _*)


    for {
      x <- controller.index(year, month, direction)(request)
      y <- controller.bar(year, month, direction)(request)
      table <- Pagelet.readBody(x)
      bar <- Pagelet.readBody(y)
    } yield Ok(views.html.aggregator.aggregator(table, bar)).withCookies(Pagelet.mergeCookies(x, y): _*)
    */
  }
}