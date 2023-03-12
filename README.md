# ff4s

A minimal purely-functional web frontend framework for [Scala.js](https://www.scala-js.org/).

Based on these wonderful libraries:
 - [Cats](https://typelevel.org/cats/)
 - [Cats-Effect](https://typelevel.org/cats-effect/)
 - [FS2](https://fs2.io/)
 - [fs2-dom](https://github.com/armanbilge/fs2-dom)
 - [http4s](https://http4s.org/)
 - [Scala DOM Types](https://github.com/raquo/scala-dom-types)
 - [Snabbdom](https://github.com/snabbdom/snabbdom) (actually the Scala.js port [scala-js-snabbdom](https://github.com/buntec/scala-js-snabbdom))

Inspired by:
  - [Outwatch](https://github.com/outwatch/outwatch)
  - [Laminar](https://github.com/raquo/Laminar)
  - [Calico](https://github.com/armanbilge/calico)

See the `examples` folder for commented code examples.

You can try the examples by running `examples/fastLinkJS` in sbt and serving
the `index.html` using something like [Live Server](https://www.npmjs.com/package/live-server).

For good measure, there is an implementation of [todomvc](https://github.com/tastejs/todomvc)
in the `todo-mvc` folder.

```scala
libraryDependencies += "io.github.buntec" %%% "ff4s" % "<x.y.z>"
```
