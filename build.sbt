Global / onChangedBuildSource := ReloadOnSourceChanges
Global / resolvers += "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

ThisBuild / tlBaseVersion := "0.15"

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

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

ThisBuild / tlFatalWarningsInCi := false

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

lazy val scalajsDomVersion = "2.4.0"
lazy val circeVersion = "0.14.5"
lazy val catsVersion = "2.9.0"
lazy val catsEffectVersion = "3.4.10"
lazy val fs2Version = "3.6.1"
lazy val kindProjectorVersion = "0.13.2"
lazy val http4sDomVersion = "0.2.8"
lazy val http4sVersion = "0.23.18"
lazy val betterMonadicForVersion = "0.3.1"
lazy val scalaJsSnabbdomVersion = "0.2.0-M3"
lazy val fs2DomVersion = "0.2.0-RC3"

lazy val generateDomDefs = taskKey[Seq[File]]("Generate SDT sources")

lazy val root = tlCrossRootProject.aggregate(ff4s, examples, todoMvc)

lazy val ff4s = (project in file("ff4s"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "ff4s",
    libraryDependencies ++= Seq(
      ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0")
        .cross(CrossVersion.for3Use2_13),
      "io.github.buntec" %%% "scala-js-snabbdom" % scalaJsSnabbdomVersion,
      "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
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
    ),
    Compile / generateDomDefs := {
      import cats.effect.unsafe.implicits.global
      import sbt.util.CacheImplicits._
      val store = (streams.value.cacheStoreFactory / "ff4s").make("dom-defs")
      val cachedDomDefs = Cache.cached(store)((_: String) =>
        DomDefsGenerator
          .generate((Compile / sourceManaged).value / "domdefs")
          .unsafeRunSync()
      )
      cachedDomDefs((Compile / scalaVersion).value)
    },
    Compile / sourceGenerators += (Compile / generateDomDefs)
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

import laika.ast.LengthUnit
import laika.ast.Styles
import laika.theme.config._
import laika.helium.config.IconLink
import laika.helium.config.HeliumIcon
import laika.helium.config.ColorQuintet
import laika.helium.Helium
import LengthUnit._

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    tlSiteApiPackage := Some("ff4s"),
    mdocJS := Some(ff4s),
    tlSiteRelatedProjects ++= Seq(
      TypelevelProject.CatsEffect,
      TypelevelProject.Fs2,
      "fs2-dom" -> url("https://github.com/armanbilge/fs2-dom/"),
      "http4s-dom" -> url("https://http4s.github.io/http4s-dom/")
    ),
    laikaConfig ~= { _.withRawContent },
    tlSiteHeliumConfig ~= { helium =>
      val `sky-800` = Color.hex("075985")
      val `sky-600` = Color.hex("0284c7")
      val `sky-400` = Color.hex("38bdf8")
      val `fuchsia-800` = Color.hex("86198f")
      val `fuchsia-700` = Color.hex("a21caf")
      val `fuchsia-600` = Color.hex("c026d3")
      val `fuchsia-300` = Color.hex("f0abfc")
      val `fuchsia-100` = Color.hex("fae8ff")
      val `pink-500` = Color.hex("ec4899")
      val `teal-600` = Color.hex("0d9488")
      val `teal-700` = Color.hex("0f766e")
      val `teal-800` = Color.hex("115e59")
      val `gray-700` = Color.hex("374151")
      val `gray-50` = Color.hex("f9fafb")
      val `gray-100` = Color.hex("f3f4f6")
      val `gray-200` = Color.hex("e5e7eb")
      val `gray-300` = Color.hex("d1d5db")
      val `gray-400` = Color.hex("9ca3af")
      val `gray-500` = Color.hex("6b7280")
      val `red-600` = Color.hex("dc2626")
      val `red-400` = Color.hex("f87171")
      val `orange-600` = Color.hex("ea580c")
      val `orange-400` = Color.hex("fb923c")
      helium.all
        .fontResources(
          FontDefinition(
            Font.webCSS("https://fonts.googleapis.com/css?family=Lato:100,300"),
            "Lato",
            FontWeight.Normal,
            FontStyle.Normal
          ),
          FontDefinition(
            Font.webCSS(
              "https://cdn.jsdelivr.net/npm/firacode@6.2.0/distr/fira_code.css"
            ),
            "Fira Code",
            FontWeight.Normal,
            FontStyle.Normal
          )
        )
        .all
        .fontFamilies(
          "Lato",
          "Lato",
          "Fira Code"
        )
        .all
        .fontSizes(
          body = px(18),
          code = px(14),
          title = px(34),
          header2 = px(30),
          header3 = px(22),
          header4 = px(18),
          small = px(12)
        )
        .all
        .themeColors(
          primary = `sky-800`, // dark teal
          secondary = `gray-700`, // dark red
          primaryMedium = `gray-200`, // light teal
          primaryLight = `gray-100`, // very light teal
          text = `gray-700`, // dark gray
          background = `gray-50`, // white
          bgGradient = (
            Color.hex("095269"),
            Color.hex("007c99")
          ) // from dark blue to light blue
        )
        .all
        .messageColors(
          info = `sky-600`,
          infoLight = `sky-400`,
          warning = `orange-600`,
          warningLight = `orange-400`,
          error = `red-600`,
          errorLight = `red-400`
        )
        .all
        .syntaxHighlightingColors(
          base = ColorQuintet(
            `gray-100`, // background
            `gray-400`, // comments
            `gray-300`, // ???
            `fuchsia-800`, // variables
            `gray-700` // parens, braces, brackets, commas, etc.
          ),
          wheel = ColorQuintet(
            `pink-500`, // string interpolation
            `sky-800`, // language keywords
            `teal-600`, // method names
            `teal-800`, // literals: numbers, strings, etc.
            `sky-600` // class, type, object names
          )
        )
        .site
        .autoLinkJS() // Actually, this *disables* auto-linking, to avoid duplicates with mdoc
        .site
        .topNavigationBar(navLinks =
          Seq(
            IconLink.external(
              "https://github.com/buntec/ff4s",
              HeliumIcon.github,
              options = Styles("svg-link")
            )
          )
        )
    }
  )
