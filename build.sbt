scalaVersion := "3.3.1"

val http4sV = "0.23.26"
val ceV     = "3.5.4"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % ceV,

  "org.http4s" %% "http4s-ember-server" % http4sV,
  "org.http4s" %% "http4s-ember-client" % http4sV,
  "org.http4s" %% "http4s-dsl"          % http4sV,
  "org.http4s" %% "http4s-circe"        % http4sV,

  "io.circe" %% "circe-generic" % "0.14.7",
  "io.circe" %% "circe-parser"  % "0.14.7"
)