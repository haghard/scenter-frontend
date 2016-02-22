package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializerSettings

trait MaterializerSupport {
  mixin: {
    def system: ActorSystem
    def log: org.slf4j.Logger
  } =>

  lazy val decider: akka.stream.Supervision.Decider = {
    case ex: Throwable ⇒
      log.debug("Akka-Stream error:" + ex.getMessage)
      akka.stream.Supervision.Stop
  }

  lazy val settings = ActorMaterializerSettings.create(system)
    .withInputBuffer(32, 32)
    .withSupervisionStrategy(decider)
    .withDispatcher("akka.stream-dispatcher")

  implicit val Mat = akka.stream.ActorMaterializer(settings)(system)
  implicit val Ex = Mat.executionContext
}
