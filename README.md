# ff4s

![Maven Central](https://img.shields.io/maven-central/v/io.github.buntec/ff4s_sjs1_2.13)

Check out the [microsite](https://buntec.github.io/ff4s/) and [Scaladocs](https://www.javadoc.io/doc/io.github.buntec/ff4s_sjs1_3/latest/index.html).

See the `examples` folder for commented code examples.
Try them out by running `examples/fastLinkJS` in sbt and serving
the `index.html` using something like [Live Server](https://www.npmjs.com/package/live-server).

For good measure, there is an implementation of [todomvc](https://github.com/tastejs/todomvc)
in the `todo-mvc` folder.

Artifacts are published to Maven Central for Scala 2.13 and Scala 3.

```scala
libraryDependencies += "io.github.buntec" %%% "ff4s" % "<x.y.z>"
```

## Debugging

You can query the state of your ff4s app in the Browser console by defining/declaring the
following global variables (e.g., by adding an inline script to your `index.html`):

```javascript
var process = {
  env: {
    'FF4S_DEBUG': 'TRUE',
  }
};
var ff4s_state;
```

The current state can then be retrieved from the `ff4s_state` variable. In particular,
you can call `ff4s_state.toString()` (and `toString` can be customized in your Scala code).

