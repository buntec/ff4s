# ff4s
![Maven Central](https://img.shields.io/maven-central/v/io.github.buntec/ff4s_sjs1_2.13)

A minimal purely-functional web UI framework for [Scala.js](https://www.scala-js.org/).

Thanks to amazing work by [@yurique](https://github.com/yurique), you can now [try it from your browser](https://scribble.ninja/).

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

Artifacts are published to Maven Central for Scala 2.13 and Scala 3.

```scala
libraryDependencies += "io.github.buntec" %%% "ff4s" % "<x.y.z>"
```

## Getting Started

The programming model of ff4s is inspired by Flux/Redux.
The view (what is rendered to the DOM) is a pure function of the state.
State is immutable and can be updated through actions dispatched to the
store (e.g., by clicking a button).
There is a single store that encapsulates all logic for updating state.
Actions can trigger side-effects (e.g., making a REST call or sending a WebSocket message).

To illustrate this with an example, let's implement the "Hello, World!" of UIs:
A counter that can be incremented or decremented by clicking a button.

In Scala, the natural choice for an immutable state container is a case class:

```scala
// State.scala
final case class State(counter: Int = 0)
```

State can only be updated through actions dispatched to the store.
We typically encode the set of actions as an ADT:

```scala
// Action.scala
sealed trait Action
case class Inc(amount: Int) extends Action
case class Reset() extends Action
```

With the `State` and `Action` types in hand, we can set up our store,
which handles all state updates and side-effects (none in this example).

```scala
import cats.syntax.all._

val store = Resource[F, ff4s.Store[F, State, Action]] = 
    ff4s.Store[F, State, Action](State()) {
      _ match {
        case Inc(amount) =>
          state => state.copy(counter = state.counter + amount) -> none.pure[F]
        case Reset() => _.copy(counter = 0) -> none.pure[F]
      }
    }
```

The purpose of `none.pure[F]` will become clear when looking at more complex examples.
Think of them as placeholders for side-effects such as fetching data from the back-end.

The fact that `store` is a `Resource` is extremely useful because it allows
us to do interesting things in the background (think WebSockets,
subscribing to state changes, etc.).
Be sure to check out the examples provided in this repo to see more elaborate
store logic, including WebSocket messaging and REST calls.

Finally, we describe how our page should be rendered using the built-in DSL
for HTML markup:

```scala
import dsl._ // `dsl` is provided by `ff4s.App`, see below
import dsl.html._

val view = useState { state =>
    div(
        cls := "m-2 flex flex-col items-center", // tailwindcss classes
        h1("A counter"),
        div(s"value: ${state.counter}"),
        button(
        cls := "m-1 p-1 border",
        "increment",
        onClick := (_ => Some(Inc(1)))
        ),
        button(
        cls := "m-1 p-1 border",
        "decrement",
        onClick := (_ => Some(Inc(-1)))
        ),
        button(
        cls := "m-1 p-1 border",
        "reset",
        onClick := (_ => Some(Reset()))
        )
    )
}
```

To turn this into an app, all we need to do is implement the `ff4s.App`
trait using `store` and `view` from above and pass an
instance of it to the `IOEntryPoint` class, which in turn defines an
appropriate `main` method for us:

```scala
// App.scala

// A basic store requires `cats.effect.Concurrent[F]`.
// In real-world applications we usually need the more powerful `cats.effect.Async[F]`.
class App[F[_]](implicit F: Concurrent[F]) extends ff4s.App[F, State, Action] {
    override val store = ??? // as above
    override val view = ??? // as above
}

// Main.scala
object Main extends ff4s.IOEntryPoint(new App) // uses cats.effect.IO for F
```
