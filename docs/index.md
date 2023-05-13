# ff4s

A minimal purely-functional web UI library for [Scala.js](https://www.scala-js.org/).

Thanks to amazing work by [@yurique](https://github.com/yurique),
you can now try **ff4s** on [scribble.ninja](https://scribble.ninja/).

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

## Dependencies

Artifacts are published to Maven Central for Scala 2.13 and Scala 3.

```scala
libraryDependencies += "io.github.buntec" %%% "ff4s" % "@VERSION@"
```
