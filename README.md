# ff4s

A purely functional web frontend framework for [Scala.js](https://www.scala-js.org/).

Based on [Snabbdom](https://github.com/snabbdom/snabbdom), [Cats](https://typelevel.org/cats/), [Cats-Effect](https://typelevel.org/cats-effect/), [FS2](https://fs2.io/), [Scala DOM Types](https://github.com/raquo/scala-dom-types).

Heavily inspired by [Outwatch](https://github.com/outwatch/outwatch).

See the `examples` folder for commented code examples.

To run an example locally, at the sbt prompt change to the `examples` project, then run `dev`.

If you want to give ff4s a spin, add this to your `build.sbt`:
```
enablePlugins(ScalaJSPlugin)
enablePlugins(ScalaJSBundlerPlugin)
resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += "com.github.buntec.ff4s" %%% "ff4s" % "0.1.1"
```
(You can also use the latest short commit hash as the version string).
Be sure to add these plugins to your `project/plugins.sbt`:
```
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.8.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")
```

CAVEAT: This is very much work in progress!
