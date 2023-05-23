# A Counter

Let's implement the "Hello, World!" of UIs:
A counter that can be incremented or decremented by clicking a button.

## State

In Scala, the natural choice for an immutable state container is a case class:

```scala mdoc:js:shared
final case class State(counter: Int = 0)
```

## Actions

State can only be updated through actions dispatched to the store.
We typically encode the set of actions as an ADT:

```scala mdoc:js:shared
sealed trait Action
case class Inc(amount: Int) extends Action
case object Reset extends Action
```

## Store

With the `State` and `Action` types in hand, we can set up our store:

```scala mdoc:js:shared
import cats.effect._
import cats.syntax.all._

object Store {

  // A basic store requires `cats.effect.Concurrent[F]`.
  // In real-world app we usually need the more powerful `cats.effect.Async[F]`.
  def apply[F[_]: Concurrent]: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { _ =>
      _ match {
        case Inc(amount) =>
          state => state.copy(counter = state.counter + amount) -> none
        case Reset => _.copy(counter = 0) -> none
      }
    }

}
```

The purpose of `none` will become clear when looking at more interesting examples
involving side-effects such as fetching data from the back-end.

The fact that `store` is a `Resource` is extremely useful because it allows
us to do interesting things in the background (think WebSockets,
subscribing to state changes, etc.).
Be sure to check out the other examples to see more elaborate store logic.

## View

Finally, we describe how our page should be rendered using the built-in DSL
for HTML markup:

```scala mdoc:js:shared
object View {

  def apply[F[_]](implicit dsl: ff4s.Dsl[F, State, Action]) = {

    import dsl._
    import dsl.html._

    useState { state =>
      div(
        cls := "counter-example", // cls b/c class is a reserved keyword in scala
        div(s"value: ${state.counter}"),
        button(
          cls := "counter-button",
          "Increment",
          onClick := (_ => Some(Inc(1)))
        ),
        button(
          cls := "counter-button",
          "Decrement",
          onClick := (_ => Some(Inc(-1)))
        ),
        button(
          cls := "counter-button",
          "Reset",
          onClick := (_ => Some(Reset))
        )
      )
    }

  }
}
```

## App

To turn this into an app, we need a small amount of boilerplate:

```scala mdoc:js:compile-only
// App.scala
class App[F[_]](implicit F: Concurrent[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
}

// Main.scala
// this defines an appropriate `main` method for us
object Main extends ff4s.IOEntryPoint(new App) // uses cats.effect.IO for F
```

```scala mdoc:js:invisible
class App[F[_]](implicit F: Concurrent[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
