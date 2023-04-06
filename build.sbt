Global / onChangedBuildSource := ReloadOnSourceChanges
Global / resolvers += "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

ThisBuild / tlBaseVersion := "0.9"

lazy val scala213 = "2.13.10"
ThisBuild / scalaVersion := scala213
ThisBuild / crossScalaVersions := Seq(scala213, "3.2.2")

ThisBuild / organization := "io.github.buntec"
ThisBuild / organizationName := "buntec"
ThisBuild / startYear := Some(2022)
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / developers := List(
  tlGitHubDev("buntec", "Christoph Bunte")
)

ThisBuild / tlFatalWarningsInCi := false

lazy val scalajsDomVersion = "2.4.0"
lazy val domtypesVersion = "0.15.3"
lazy val circeVersion = "0.14.5"
lazy val catsVersion = "2.9.0"
lazy val catsEffectVersion = "3.4.8"
lazy val fs2Version = "3.6.1"
lazy val kindProjectorVersion = "0.13.2"
lazy val http4sDomVersion = "0.2.7"
lazy val http4sVersion = "0.23.18"
lazy val betterMonadicForVersion = "0.3.1"
lazy val scalaJsSnabbdomVersion = "0.2.0-M3"
lazy val fs2DomVersion = "0.2.0-RC3"

lazy val root = tlCrossRootProject.aggregate(ff4s, examples, todoMvc)

lazy val ff4s = (project in file("ff4s"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "ff4s",
    libraryDependencies ++= Seq(
      "io.github.buntec" %%% "scala-js-snabbdom" % scalaJsSnabbdomVersion,
      "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
      "com.raquo" %%% "domtypes" % domtypesVersion,
      "org.typelevel" %%% "cats-core" % catsVersion,
      "org.typelevel" %%% "cats-free" % catsVersion,
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "org.typelevel" %%% "cats-effect-kernel" % catsEffectVersion,
      "org.typelevel" %%% "cats-effect-std" % catsEffectVersion,
      "co.fs2" %%% "fs2-core" % fs2Version,
      "org.http4s" %%% "http4s-dom" % http4sDomVersion,
      "org.http4s" %%% "http4s-client" % http4sVersion,
      "org.http4s" %%% "http4s-circe" % http4sVersion,
      "io.circe" %%% "circe-generic" % circeVersion,
      "io.circe" %%% "circe-literal" % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion,
      "com.armanbilge" %%% "fs2-dom" % fs2DomVersion
    )
  )

lazy val examples = (project in file("examples"))
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "dev.optics" %%% "monocle-core" % "3.2.0",
      "dev.optics" %%% "monocle-macro" % "3.2.0"
    )
  )
  .dependsOn(ff4s)

lazy val todoMvc = (project in file("todo-mvc"))
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := true
  )
  .dependsOn(ff4s)
