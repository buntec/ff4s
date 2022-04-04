Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / scalaVersion := "2.13.8"

ThisBuild / organization := "com.github.buntec"
ThisBuild / organizationName := "buntec"

lazy val scalajsDomVersion = "2.1.0"
lazy val domtypesVersion = "0.15.1"
lazy val circeVersion = "0.15.0-M1"
lazy val catsVersion = "2.7.0"
lazy val catsEffectVersion = "3.3.10"
lazy val fs2Version = "3.2.5"
lazy val kindProjectorVersion = "0.13.2"
lazy val http4sDomVersion = "0.2.1"
lazy val http4sVersion = "0.23.11"
lazy val betterMonadicForVersion = "0.3.1"

lazy val root = (project in file("."))
  .settings(publish / skip := true)
  .aggregate(ff4s, examples)

lazy val ff4s = (project in file("ff4s"))
  .enablePlugins(ScalaJSBundlerPlugin, ScalaJSPlugin, GitVersioning)
  .settings(
    name := "ff4s",
    git.useGitDescribe := true,
    crossScalaVersions := Seq("2.12.15", "2.13.8"),
    scalacOptions -= "-Xfatal-warnings",
    Compile / npmDependencies ++= Seq(
      "snabbdom" -> "3.2.0",
      "snabby" -> "4.2.4"
    ),
    libraryDependencies ++= Seq(
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
      compilerPlugin(
        "com.olegpy" %% "better-monadic-for" % betterMonadicForVersion
      ),
      compilerPlugin(
        "org.typelevel" % "kind-projector" % kindProjectorVersion cross CrossVersion.full
      )
    )
  )

lazy val examples = (project in file("examples"))
  .enablePlugins(ScalaJSBundlerPlugin, ScalaJSPlugin)
  .settings(
    name := "examples",
    publish / skip := true,
    scalacOptions -= "-Xfatal-warnings",
    useYarn := true,
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(
      ModuleKind.CommonJSModule
    )), // configure Scala.js to emit a JavaScript module instead of a top-level script
    webpack / version := "5.65.0",
    webpackCliVersion := "4.9.1",
    startWebpackDevServer / version := "4.7.1",
    webpackDevServerExtraArgs := Seq("--color"),
    webpackDevServerPort := 8080,
    fastOptJS / webpackConfigFile := Some(
      baseDirectory.value / "webpack.config.dev.js"
    ),
    fastOptJS / webpackBundlingMode := BundlingMode
      .LibraryOnly(), // https://scalacenter.github.io/scalajs-bundler/cookbook.html#performance
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
      "io.circe" %%% "circe-generic" % circeVersion,
      "io.circe" %%% "circe-literal" % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion,
      compilerPlugin(
        "com.olegpy" %% "better-monadic-for" % betterMonadicForVersion
      ),
      compilerPlugin(
        "org.typelevel" % "kind-projector" % kindProjectorVersion cross CrossVersion.full
      )
    )
  )
  .dependsOn(ff4s)

// hot reloading configuration:
// https://github.com/scalacenter/scalajs-bundler/issues/180
addCommandAlias(
  "dev",
  "; compile; fastOptJS::startWebpackDevServer; devwatch; fastOptJS::stopWebpackDevServer"
)
addCommandAlias("devwatch", "~; fastOptJS; copyFastOptJS")

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.

// when running the "dev" alias, after every fastOptJS compile all artifacts are copied into
// a folder which is served and watched by the webpack devserver.
// this is a workaround for: https://github.com/scalacenter/scalajs-bundler/issues/180
lazy val copyFastOptJS =
  TaskKey[Unit]("copyFastOptJS", "Copy javascript files to target directory")

examples / copyFastOptJS := {
  val inDir = (examples / Compile / fastOptJS / crossTarget).value
  val outDir =
    (examples / Compile / fastOptJS / crossTarget).value / "dev"
  val files = Seq(
    (examples / name).value.toLowerCase + "-fastopt-loader.js",
    (examples / name).value.toLowerCase + "-fastopt.js",
    (examples / name).value.toLowerCase + "-fastopt.js.map"
  ) map { p => (inDir / p, outDir / p) }
  IO.copy(
    files,
    overwrite = true,
    preserveLastModified = true,
    preserveExecutable = true
  )
}
