package frontend

import controllers.routes
import jp.t2v.lab.play2.auth._
import org.joda.time.{DateTime, Interval}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Results._
import play.api.mvc._

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext}
import scala.reflect.ClassTag

trait AuthorizationConfig extends AuthConfig {
  mixin: { def conf: play.api.Configuration } =>

  override type Id = String
  override type User = frontend.Account
  override type Authority = frontend.Permission
  override val idTag: ClassTag[Id] = scala.reflect.classTag[Id]
  override val sessionTimeoutInSeconds = tokenMaxAge

  lazy val intervals: mutable.LinkedHashMap[Interval, String] = loadStages(conf.getObjectList("stages"))

  override def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] = {
    val fields = id.split(",")
    val id0 = fields(0)
    val login = fields(1)
    val password = fields(2)
    val permission = fields(3)
    val token = fields(4)
    Future.successful(Option(Account(id0.toInt, login, password, permission, token)))
  }

  /**
    * Where to redirect the user after a successful login.
    */
  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    val stage = (for {
      (k, v) ← intervals
      if( k contains new DateTime(System.currentTimeMillis()).withZone(frontend.EST) )
    } yield v).headOption
    stage.fold(Future.successful(play.api.mvc.Results.Forbidden("Current stage isn't found"))) { stage =>
      Future.successful(play.api.mvc.Results.Redirect(routes.Aggregator.index(stage)))
    }
  }

  /**
    * Where to redirect the user after logging out
    */
  def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Redirect(routes.SportCenter.login(loginError = false)))
  }


  /**
    * If the user is not logged in and tries to access a protected resource then redirect them as follows:
    */
  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Redirect(routes.SportCenter.login(loginError = true)))
  }


  /**
    * If authorization failed (usually incorrect password) redirect the user as follows:
    */
  override def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])
                                  (implicit context: ExecutionContext): Future[Result] =
    Future.successful(play.api.mvc.Results.Forbidden(s"resource is forbidden for ${user.login}"))

  /**
    * A function that determines what `Authority` a user has.
    * You should alter this procedure to suit your application.
    */
  def authorize(user: User, authority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = Future.successful {
    (Permission.valueOf(user.permission), authority) match {
      case (Administrator, _)       => true
      case (RegularUser, _) => true
      case (TwitterUser, _) => true
      case _                 => false
    }
  }

  /**
    * (Optional)
    * You can custom SessionID Token handler.
    * Default implementation use Cookie.
    */
  override lazy val tokenAccessor = new CookieTokenAccessor(
    /*
     * Whether use the secure option or not use it in the cookie.
     * Following code is default.
     */
    cookieName         = conf.getString("http-session.header").getOrElse("AUTH_SESS_ID"),
    cookieSecureOption = play.api.Play.isProd(play.api.Play.current),
    cookieMaxAge       = Some(sessionTimeoutInSeconds)
  )

  def authenticationErrorForm(errorMessage: String) = Form {
    mapping(("login" -> nonEmptyText), ("password" -> nonEmptyText))({(l,r) => None: Option[frontend.Account]})(_ => None)
      .verifying("Invalid login or password", result => result.isDefined)
  }.withGlobalError(errorMessage)

  def loginUrl = conf.getString("url.login").get

  //should be equal to backend Max-Age
  def tokenMaxAge = conf.getInt("http-session.max-age").get
}