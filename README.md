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

You can try these examples by serving the `index.html` using a simple
development server, e.g., [Live Server](https://www.npmjs.com/package/live-server).

There is also an implementation of [todomvc](https://github.com/tastejs/todomvc)
in the `todo-mvc` folder.

To use ff4s in your own project, add this to your `build.sbt`:
```scala
enablePlugins(ScalaJSPlugin)
resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += "io.github.buntec.ff4s" %%% "ff4s" % "0.1.8"
```
(You can also use the latest short commit hash as the version string.)

Disclaimer: this is work in progress!
