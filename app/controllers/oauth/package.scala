package controllers

package object oauth {
  import java.util.concurrent.ThreadLocalRandom
  import scala.annotation.implicitNotFound

  trait Oauth[T <: com.github.scribejava.core.builder.api.Api] {
    var apiKey: String = _
    var apiSecret: String = _

    val header = "oauth_token"
    val headerV = "oauth_verifier"

    def protectedUrl: String

    protected def state = s"secret-${ThreadLocalRandom.current().nextInt(Int.MinValue, Int.MaxValue)}"

    def oAuthService: com.github.scribejava.core.builder.ServiceBuilder

    def fromConfig(apiKey0: String, apiSecret0: String): Oauth[T] = {
      apiKey = apiKey0
      apiSecret = apiSecret0
      this
    }

    def instance: T
  }

  object Oauth {

    @implicitNotFound(msg = "Cannot find Oauth type class for ${T}")
    def apply[T <: com.github.scribejava.core.builder.api.Api: Oauth]: Oauth[T] = implicitly[Oauth[T]]

    implicit def twitter = new Oauth[com.github.scribejava.apis.TwitterApi] {
      override val protectedUrl = "https://api.twitter.com/1.1/account/verify_credentials.json"

      override def instance = com.github.scribejava.apis.TwitterApi.instance()

      override def oAuthService() =
        new com.github.scribejava.core.builder.ServiceBuilder()
          .apiKey(apiKey)
          .apiSecret(apiSecret)
    }

    implicit def github = new Oauth[com.github.scribejava.apis.GitHubApi] {
      override val protectedUrl = "https://api.github.com/user"

      override def instance = com.github.scribejava.apis.GitHubApi.instance()

      override def oAuthService() =
        new com.github.scribejava.core.builder.ServiceBuilder()
          .apiKey(apiKey)
          .apiSecret(apiSecret)
          .state(state)
    }
  }
}