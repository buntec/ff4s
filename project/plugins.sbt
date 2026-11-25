val sbtTypelevelVersion = "0.8.7"
addSbtPlugin("org.typelevel" % "sbt-typelevel" % sbtTypelevelVersion)
addSbtPlugin("org.typelevel" % "sbt-typelevel-site" % sbtTypelevelVersion)
addSbtPlugin("org.typelevel" % "sbt-typelevel-scalafix" % sbtTypelevelVersion)
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.23.0")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.7.1")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.9.3")
libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.1"
