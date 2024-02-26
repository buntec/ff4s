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

## Companion libraries

- [ff4s-canvas](https://github.com/buntec/ff4s-canvas)
- [ff4s-shoelace](https://github.com/buntec/ff4s-shoelace)
- [ff4s-heroicons](https://github.com/buntec/ff4s-heroicons)


## (Breaking) changes

### 0.23.0
- Adds `withClass` extension method to the `V` type that allows overriding the `class` attribute of the underlying node.
  This turns out to be useful for `literal`s, e.g., setting the class on an SVG icon.

### 0.22.0
- `Dsl[F, State, Action]` becomes `Dsl[State, Action]`. The `F` parameter was merely an implementation detail leaking out.
- `ff4s.App` no longer has an implicit `Dsl` member. A better pattern for organizing components is to use a `Dsl[State, Action]` self-type (see the examples),
  which obviates the need for passing a `Dsl` parameter to every component.

### 0.21.0
- Adds a debug mode for inspecting the state from the browser console.
- Improves support for web components by adding a `WebComponent` base trait and a `Slot` modifier (see [ff4s-shoelace](https://github.com/buntec/ff4s-shoelace) for usage examples).

### 0.20.0
- Bug fix: map reflected attributes to attributes instead of props. Properties typically cannot be deleted so things like `id`, once set, couldn't be removed.

### 0.18.0
- Adds caching for `literal`s. This can significantly improve performance, e.g., when displaying a large number of SVG icon literals.


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
