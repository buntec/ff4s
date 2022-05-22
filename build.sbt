Global / onChangedBuildSource := ReloadOnSourceChanges
// Global / resolvers += "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

ThisBuild / scalaVersion := "2.13.8"

ThisBuild / organization := "com.github.buntec"
ThisBuild / organizationName := "buntec"

lazy val scalajsDomVersion = "2.2.0"
lazy val domtypesVersion = "0.15.1"
lazy val circeVersion = "0.15.0-M1"
lazy val catsVersion = "2.7.0"
lazy val catsEffectVersion = "3.3.12"
lazy val fs2Version = "3.2.7"
lazy val kindProjectorVersion = "0.13.2"
lazy val http4sDomVersion = "0.2.1"
lazy val http4sVersion = "0.23.11"
lazy val betterMonadicForVersion = "0.3.1"

lazy val scalaJsSnabbdomVersion = "0.1.0-M2"

lazy val root = (project in file("."))
  .settings(publish / skip := true)
  .aggregate(ff4s, examples)

lazy val ff4s = (project in file("ff4s"))
  .enablePlugins(ScalaJSPlugin, GitVersioning)
  .settings(
    name := "ff4s",
    git.useGitDescribe := true,
    crossScalaVersions := Seq("2.13.8"),
    scalacOptions -= "-Xfatal-warnings",
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
      compilerPlugin(
        "com.olegpy" %% "better-monadic-for" % betterMonadicForVersion
      ),
      compilerPlugin(
        "org.typelevel" % "kind-projector" % kindProjectorVersion cross CrossVersion.full
      )
    )
  )

lazy val examples = (project in file("examples"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "examples",
    publish / skip := true,
    scalacOptions -= "-Xfatal-warnings",
    scalaJSUseMainModuleInitializer := true,
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

lazy val todoMvc = (project in file("todo-mvc"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "todo-mvc",
    publish / skip := true,
    scalacOptions -= "-Xfatal-warnings",
    scalaJSUseMainModuleInitializer := true,
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
