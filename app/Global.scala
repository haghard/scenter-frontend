import java.net.{NetworkInterface, InetAddress}

import play.api.{Play, Application, GlobalSettings}
import play.api.libs.ws.ahc.{AhcConfigBuilder, AhcWSClient}
import play.api.mvc.Results._
import play.api.mvc.RequestHeader
import frontend._
import slick.driver.H2Driver.api._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._

object Global extends GlobalSettings {
  implicit val timeout = 5 seconds

  val Sep = ","
  val AuthHeader = "Set-Authorization"
  val logger = akka.event.slf4j.Logger("global")

  val ipExpression = """\d{1,3}.\d{1,3}.\d{1,3}.\d{1,3}"""

  def addresses(ethName: String): Option[InetAddress] =
    NetworkInterface.getNetworkInterfaces.asScala.toList
      .find(_.getName == ethName)
      .flatMap(x ⇒ x.getInetAddresses.asScala.toList.find(i ⇒ i.getHostAddress.matches(ipExpression)))

  override def onStart(app: Application) {
    val extIp = app.configuration.getString("http.ip").fold(throw new Exception(s"DOMAIN env variable should be passed"))(identity)
    val localAddress = addresses("eth0").map(_.getHostAddress).getOrElse("0.0.0.0")

    val message = new StringBuilder().append('\n')
      .append("=====================================================================================================================================")
      .append("\n")
      .append(s"★ ★ ★ ★ ★ ★ Frontend env:${Play.mode(app)} network:[ext: $extIp - docker: $localAddress] ★ ★ ★ ★ ★ ★")
      .append("\n")
      .append("=====================================================================================================================================")

    logger.info(message.toString())

    implicit val ec = app.actorSystem.dispatchers.lookup("akka.blocking-dispatcher")
    AccountModel.readCount.fold(createSchema(app)) { count => logger.info(s"Registered users are founded:$count") }
  }

  def createSchema(app: Application)(implicit ec: ExecutionContext) = {
    val haghard = Account(0, DefaultLogin, org.mindrot.jbcrypt.BCrypt.hashpw(DefaultPassword, AccountModel.salt), "RegularUser", DefaultToken)
    val loginUrl = app.configuration.getString("url.login").get
    val client = new AhcWSClient(new AhcConfigBuilder().build())(app.materializer)

    val insertFlow = AccountModel.refreshToken(loginUrl, haghard, AuthHeader, client).flatMap { haghard =>
      haghard.fold(Future.failed[Unit](new Exception(""))) { haghardWithToken =>
        DB.connection.run(
          DBIO.seq(
            AccountModel.accounts.schema.create,
            AccountModel.accounts += haghardWithToken,
            AccountModel.accounts.result.map(a => logger.info("Default user has been inserted: {}", a.head.login))
          )
        )
      }
    }
    insertFlow.onFailure { case e: Exception => logger.info("Default user init procedure has failed {}", e.getMessage) }
    insertFlow.onComplete(_ => client.close)
  }

  override def onStop(app: Application): Unit = {
    logger.info("★ ★ ★ ★ ★ ★ Application is stopped ★ ★ ★ ★ ★ ★")
    DB.connection.close()
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    Future.successful(BadRequest(s"Bad Request: $error"))
  }

  // 500 - internal server error
  override def onError(request: RequestHeader, throwable: Throwable) = {
    Future.successful(InternalServerError(views.html.errors.onError(throwable)))
  }

  // 404 - page not found error
  override def onHandlerNotFound(request: RequestHeader) = {
    Future.successful(NotFound(views.html.errors.onHandlerNotFound(request)))
  }
}