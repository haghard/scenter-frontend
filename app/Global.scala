import play.api.Application
import play.api.libs.ws.ahc.{AhcConfigBuilder, AhcWSClient}
import play.api.mvc.Results._
import play.api.GlobalSettings
import play.api.mvc.RequestHeader
import frontend._
import slick.driver.H2Driver.api._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object Global extends GlobalSettings {
  val Sep = ","
  val AuthHeader = "Set-Authorization"
  implicit val timeout = 5 seconds

  val logger = akka.event.slf4j.Logger("global")
  val salt = org.mindrot.jbcrypt.BCrypt.gensalt()

  override def onStart(app: Application) {
    logger.info("Application is started")
    implicit val ec = app.actorSystem.dispatchers.lookup("akka.blocking-dispatcher")
    AccountModel.readCount.fold(createSchema(app)) { count => logger.info(s"Found registered users:$count") }
  }

  def createSchema(app: Application)(implicit ec: ExecutionContext) = {
    val haghard = Account(0, "haghard", org.mindrot.jbcrypt.BCrypt.hashpw("suBai3sa", salt), "Administrator", "empty")
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
    logger.info("Application is stopped")
    DB.connection.close()
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    Future.successful(BadRequest("Bad Request: " + error))
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