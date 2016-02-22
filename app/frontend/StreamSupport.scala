package frontend

import akka.stream.{FlowShape, Attributes}
import akka.stream.scaladsl._
import scala.concurrent.duration.{Duration, FiniteDuration}

trait StreamSupport {

  def every[T](interval: FiniteDuration): Flow[T, T, akka.NotUsed] =
    Flow.fromGraph(
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._
        val zip = b.add(ZipWith[T, Unit, T](Keep.left).withAttributes(Attributes.inputBuffer(1, 1)))
        val dropOne = b.add(Flow[T].drop(1))
        Source.tick(Duration.Zero, interval, ()) ~> zip.in1
        zip.out ~> dropOne.in
        FlowShape(zip.in0, dropOne.outlet)
      }
    )

  def responseWindow(duration: FiniteDuration): Flow[play.api.libs.json.JsArray, play.api.libs.json.JsArray, akka.NotUsed] =
    (Flow[play.api.libs.json.JsArray].conflate((array, _) ⇒ array)
      .zipWith(Source.tick(duration, duration, ()))(Keep.left))
      .scan(play.api.libs.json.JsArray(Seq.empty[play.api.libs.json.JsValue]))((_, stats) => stats)
      .withAttributes(Attributes.inputBuffer(1, 1))
}
