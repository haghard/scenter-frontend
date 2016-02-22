import com.typesafe.sbt.packager.docker.Cmd
import sbt._
import play.sbt.PlayScala

//http://pedrorijo.com/blog/play-slick/
//PlayMini.scala https://gist.github.com/xuwei-k/422365f271b12603d33c
//https://engineering.sequra.es/2016/02/database-streaming-on-play-with-slick-from-publisher-to-chunked-result/


//https://www.playframework.com/documentation/2.5.x/Highlights25


//https://twitter.com/settings/widgets/700982122442579968/edit?notice=WIDGET_UPDATED

name := "scenter-frontend"

version := "0.0.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, JavaAppPackaging, DockerPlugin)

scalaVersion := "2.11.7"
javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
val CassandraDriverVersion = "3.0.0"


// Add a new template type for streaming templates
TwirlKeys.templateFormats += ("stream" -> "ui.HtmlStreamFormat")

// Add some useful default imports for streaming templates
TwirlKeys.templateImports ++= Vector("_root_.ui.HtmlStream", "_root_.ui.HtmlStream._", "_root_.ui.StaticContent")

lazy val h2Version = "1.4.191"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "com.datastax.cassandra" %   "cassandra-driver-core"    %   CassandraDriverVersion withSources(),
  "org.scalaz.stream"      %%  "scalaz-stream"            %   "0.8"                  withSources(),
  "com.esri.geometry"      %   "esri-geometry-api"        %   "1.2.1",
  "io.spray"               %%  "spray-json"               %   "1.3.2",
  "jp.t2v"                 %%  "play2-auth"               %   "0.14.0",
  "org.mindrot"            %   "jbcrypt"                  %   "0.3m",
  "com.h2database"         %   "h2"                       %   h2Version,
  "com.typesafe.slick"     %%  "slick"                    %   "3.1.1",
  "org.webjars"            %%  "webjars-play"             %   "2.4.0-2",
  "com.github.nscala-time" %%  "nscala-time"              %   "2.0.0",
  "com.github.scribejava"  %   "scribejava-core"         %    "2.0",
  "com.github.scribejava"  %   "scribejava-apis"         %    "2.0",
  specs2                   %   Test
  //"org.webjars"       %   "jquery" % "2.1.3",
  //"org.webjars"       %   "bootstrap" % "3.3.2",
)

resolvers += Resolver.mavenLocal
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator


// --------------------
// ------ DOCKER ------
// --------------------
// build with activator docker:publishLocal

dockerBaseImage := "frolvlad/alpine-oraclejdk8:latest"
maintainer := "haghard"
dockerExposedPorts in Docker := Seq(8081)
dockerBaseImage := "frolvlad/alpine-oraclejdk8:latest"

dockerCommands := dockerCommands.value.flatMap {
  case cmd@Cmd("FROM", _) => (cmd :: Cmd("RUN", "apk update && apk add bash && ls -la") :: Nil)
  case otherCmd =>  List(otherCmd)
}

//java -cp h2-1.4.191.jar org.h2.tools.Server

// publish to repo
//dockerRepository := Some("quay.io/")
//dockerUpdateLatest := true

//docker run -it -p 8080:8080 <name>:<version>