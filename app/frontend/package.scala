import java.io.File
import com.esri.core.geometry.Point
import com.typesafe.config.ConfigObject
import play.api.mvc.{Result, Request, ActionBuilder}
import scala.util.Try
import scalaz.\/

package object frontend {

  case class TweetInfo(searchQuery: String, message: String, author: String) {
    def toJson = play.api.libs.json.Json.obj("message" -> s"$searchQuery : $message", "author" -> s"$author")
  }

  type Histogram = Map[String, Long]

  import spray.json._
  import frontend.GeoJsonProtocol._

  def load(file: File): IndexedSeq[Feature] = {
    val src = scala.io.Source.fromFile(file)
    val geo = src.mkString
    src.close()
    geo.parseJson.convertTo[Features].sortBy { f ⇒
      val borough = f("boroughCode").convertTo[Int]
      (borough, -f.geometry.area2D())
    }
  }

  def borough(features: IndexedSeq[Feature], point: Point) =
    features.find(_.geometry.contains(point)).map(_("borough").convertTo[String])

  def updateHistogram(hist: Histogram, region: Option[String]) = {
    region.fold(hist) { r ⇒ hist.updated(r, hist.getOrElse(r, 0l) + 1) }
  }

  sealed trait Permission
  case object Administrator extends Permission
  case object RegularUser extends Permission
  case object TwitterUser extends Permission
  case object Anonymous extends Permission
  case object Unauthorized extends Permission

  case class Account(id: Int, login: String, password: String, permission: String, token: String = "")

  object Permission {
    def valueOf(value: String): Permission = value match {
      case "Administrator" => Administrator
      case "RegularUser"   => RegularUser
      case "Anonymous"     => Anonymous
      case v => throw new IllegalArgumentException(v)
    }
  }

  import scala.collection.JavaConverters._
  import com.github.nscala_time.time.Imports._
  import scala.collection.mutable._

  val EST = org.joda.time.DateTimeZone.forID("EST")

  def loadStages(conf: Option[java.util.List[_ <: ConfigObject]]) = {
    var views = scala.collection.mutable.LinkedHashMap[Interval, String]()
    val timeZone = EST
    var start: Option[DateTime] = None
    var end: Option[DateTime] = None
    var period: Option[String] = None

    val stages = conf.get.asScala./:(LinkedHashMap[String, String]()) { (acc, c) ⇒
      val it = c.entrySet().iterator()
      if (it.hasNext) {
        val entry = it.next()
        acc += (entry.getKey -> entry.getValue.render().replace("\"", ""))
      }
      acc
    }

    for ((k, v) ← stages) {
      if (start.isEmpty) {
        start = Option(new DateTime(v).withZone(timeZone).withTime(23, 59, 59, 0))
        period = Option(k)
      } else {
        end = Option(new DateTime(v).withZone(timeZone).withTime(23, 59, 58, 0))
        val interval = (start.get to end.get)
        views = views += (interval -> period.get)
        start = Option(end.get.withTime(23, 59, 59, 0))
        period = Option(k)
      }
    }
    views
  }


  import scala.concurrent.{ExecutionContext, Future, Promise}

  //Integration code between Scalaz and Scala standard concurrency libraries
  object Task2Future {

    def fromScala[A](future: Future[A])(implicit ec: ExecutionContext): scalaz.concurrent.Task[A] =
      scalaz.concurrent.Task.async(handlerConversion andThen future.onComplete)

    def fromScalaDeferred[A](future: => Future[A])(implicit ec: ExecutionContext): scalaz.concurrent.Task[A] =
      scalaz.concurrent.Task.delay(fromScala(future)(ec)).flatMap(identity)

    def unsafeToScala[A](task: scalaz.concurrent.Task[A]): Future[A] = {
      val p = Promise[A]
      task.runAsync { _ fold (p failure _, p success _) }
      p.future
    }

    private def handlerConversion[A]: ((Throwable \/ A) => Unit) => Try[A] => Unit =
      callback => { t: Try[A] => \/.fromTryCatchNonFatal(t.get) } andThen callback
  }

  object LoggedAction extends ActionBuilder[Request] {
    val log = akka.event.slf4j.Logger("action")

    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
      log.info(s"HTTP request: ${request.method} ${request.uri}")
      block(request)
    }
  }
}