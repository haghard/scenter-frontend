package frontend

import play.api.libs.ws.WSClient
import slick.driver.H2Driver.api._
import slick.lifted.{TableQuery, Tag}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object AccountModel {
  val accounts: TableQuery[Accounts] = TableQuery[Accounts]

  private def countQuery = sql"select count(*) from ACCOUNTS".as[Long]

  private def accountQuery(login: String) = sql"select ID, LOGIN, PASSWORD, PERMISSION, TOKEN from ACCOUNTS where LOGIN = $login"
    .as[(Int, String, String, String, String)]

  private def refreshTokenQuery(login: String, password: String, token: String) =
    sql"UPDATE ACCOUNTS SET TOKEN = $token WHERE LOGIN = $login AND PASSWORD = $password".asUpdate

  def readCount(implicit ex: ExecutionContext, duration: Duration) =
    Await.result(DB.connection.run(countQuery).map(_.headOption).recover { case ex: Exception => None }, duration)

  def authenticate2(log: org.slf4j.Logger)(login: String, password: String)(implicit ex: ExecutionContext, duration: Duration): Option[Account] = {
    log.info(s"try to lookup from account db: $login")
    Await.result(authenticate(login, password), duration)
  }

  def authenticate(login: String, password: String)(implicit ex: ExecutionContext): Future[Option[Account]] = {
    val searchAction = accountQuery(login)
    DB.connection.run(searchAction).map {
      _.find { r => org.mindrot.jbcrypt.BCrypt.checkpw(password, r._3) }
        .map(Account.tupled(_))
    }
  }

  def updateToken(login: String, password: String, token: String): Future[Int] =
    DB.connection.run(refreshTokenQuery(login, password, token))

  def refreshToken(url: String, account: Account, AuthHeader: String, ws: WSClient)(implicit ex: ExecutionContext) = {
    ws.url(url).withQueryString(("user", account.login), ("password", account.password)).get().map { res =>
      res.status match {
        case 200 => res.header(AuthHeader).map { newToken: String => account.copy(token = newToken) }
        case other => None
      }
    }.recover { case ex: Exception => None }
  }
}

class Accounts(tag: Tag) extends Table[Account](tag, "ACCOUNTS") {
  def id = column[Int]("ID", O.PrimaryKey)

  def login = column[String]("LOGIN")

  def password = column[String]("PASSWORD")

  def permission = column[String]("PERMISSION")

  def token = column[String]("TOKEN")

  override def * = (id, login, password, permission, token) <>(Account.tupled, Account.unapply)
}