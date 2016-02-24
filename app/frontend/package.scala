import com.typesafe.config.ConfigObject
import play.api.mvc.{Result, Request, ActionBuilder}
import scala.util.Try
import scalaz.\/

package object frontend {
  import scala.collection.JavaConverters._
  import com.github.nscala_time.time.Imports._
  import scala.collection.mutable._

  val DefaultLogin = "haghard"
  val DefaultPassword = "suBai3sa"
  val DefaultToken = "empty"

  val DefaultTwitterPassword = "twitter-password"

  //linked with Permission.TwitterUser
  val DefaultTwitterUser = "TwitterUser"

  val EST = org.joda.time.DateTimeZone.forID("EST")

  type Histogram = Map[String, Long]

  sealed trait Permission
  case object Administrator extends Permission
  case object RegularUser extends Permission
  case object TwitterUser extends Permission
  case object Anonymous extends Permission
  case object Unauthorized extends Permission

  case class Account(id: Long, login: String, password: String, permission: String, token: String = "")

  object Permission {
    def valueOf(value: String): Permission = value match {
      case "Administrator" => Administrator
      case "RegularUser"   => RegularUser
      case "TwitterUser"   => TwitterUser
      case "Anonymous"     => Anonymous
      case v => throw new IllegalArgumentException(v)
    }
  }

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