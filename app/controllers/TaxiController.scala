/*
package controllers

import java.io.File

import frontend._
import java.net.InetSocketAddress
import javax.inject.{Inject, Singleton}
import akka.actor.ActorSystem
import jp.t2v.lab.play2.auth.AuthElement
import play.api.libs.json.Json
import com.esri.core.geometry.Point
import play.api.mvc.Controller
import com.datastax.driver.core._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scalaz._, Scalaz._
import java.lang.{Integer => JInteger}
import akka.stream.scaladsl.{ Source => AkkaSource }

//https://dl.bintray.com/sbt/sbt-plugin-releases/com.typesafe.play/sbt-plugin/scala_2.10/sbt_0.13/

@Singleton
class TaxiController @Inject()(val conf: play.api.Configuration, system: ActorSystem, file: File) extends Controller
  with AuthElement with AuthorizationConfig with StreamSupport  {

  val features = load(file)
  val log = akka.event.slf4j.Logger("taxi-controller")
  val slidingWindow = conf.getMilliseconds("taxi.sliding-window").getOrElse(5000l) millisecond
  implicit val ex = system.dispatchers.lookup("akka.stream-dispatcher")

  val (query, session) = (for {
    keySpace <- conf.getString("cassandra.keyspace") \/> "cassandra.key-space shouldn't be empty"
    query <- conf.getString("cassandra.query") \/> "cassandra.query shouldn't be empty"
    hosts <- conf.getStringList("cassandra.hosts") \/> "cassandra.hosts shouldn't be empty"
    port <- conf.getInt("cassandra.port") \/> "cassandra.port shouldn't be empty"
  } yield (keySpace, query, port, hosts.asScala.map(new InetSocketAddress(_, port)).asJava))
    .fold({ error => log.error(error); throw new Exception(error) }, { vs =>
        (vs._2, Cluster.builder().addContactPointsWithPorts(vs._4)
            .withQueryOptions(new QueryOptions().setConsistencyLevel(ConsistencyLevel.ONE).setFetchSize(1000))
            .build.connect(vs._1))
      })

  //http://127.0.0.1:9000/taxi?year=2013&month=1&direction=dropoff
  //http://127.0.0.1:9000/taxi?year=2013&month=2&direction=pickup
  def index(year: Int, month: Int, direction: String) = AsyncStack(AuthorityKey -> RegularUser) { request =>
    Future.successful(Ok(views.html.taxi(year, month, direction)))
  }

  //http://127.0.0.1:9000/taxi/bar?year=2013&month=1&direction=dropoff
  /*LoggedAction.async*/
  def bar(year: Int, month: Int, direction: String) = AsyncStack(AuthorityKey -> RegularUser) { request =>
    Future.successful(Ok(views.html.taxibar(year, month, direction)))
  }

  def statStream(queries: String) = AsyncStack(AuthorityKey -> RegularUser) { implicit request =>
    val fields = queries.split(",")

    val iter = asScalaIterator(session.execute(query, fields(0).toInt: JInteger, fields(1).toInt: JInteger, fields(2)).iterator())
    val trips = AkkaSource.fromIterator(() => iter).map { row =>
      borough(features, new Point(row.getDouble("longitude"), row.getDouble("latitude")))
    }.scan[Histogram](Map.empty)(updateHistogram)
     .map { stats => Json.arr(stats.toVector.sortBy(-_._2).map { kv => Json.obj("message" -> s"${kv._1}", "count" -> s"${kv._2}") }) }

    Future {
      val src = trips via responseWindow(slidingWindow)
      val sse = AkkaSource.single("event: message\n").concat(src.map(statsLine => s"data: $statsLine\n\n"))
      Ok.chunked(sse).as("text/event-stream")
    }(ex)
  }
}
*/