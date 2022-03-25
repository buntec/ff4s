# ff4s

A minimal purely-functional web frontend framework for [Scala.js](https://www.scala-js.org/).

Based on these wonderful libraries:
 - [Cats](https://typelevel.org/cats/)
 - [Cats-Effect](https://typelevel.org/cats-effect/)
 - [FS2](https://fs2.io/)
 - [Scala DOM Types](https://github.com/raquo/scala-dom-types)
 - [Snabbdom](https://github.com/snabbdom/snabbdom)

Inspired by:
  - [Outwatch](https://github.com/outwatch/outwatch)
  - [Laminar](https://github.com/raquo/Laminar)

See the `examples` folder for commented code examples.

To run the example locally, at the sbt prompt change to the `examples` project, then run `dev`
and open `localhost:8080` in your browser.

If you want to try ff4s in your own project, add this to your `build.sbt`:
```scala
enablePlugins(ScalaJSPlugin)
enablePlugins(ScalaJSBundlerPlugin)
resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += "com.github.buntec.ff4s" %%% "ff4s" % "0.1.7"
```
(You can also use the latest short commit hash as the version string).
Be sure to add these plugins to your `project/plugins.sbt`:
```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.9.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")
```

CAVEAT: This is very much work in progress!
