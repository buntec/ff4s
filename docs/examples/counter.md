# A Counter

Let's implement the "Hello, World!" of UIs:
A counter that can be incremented or decremented by clicking a button.

## State

In Scala, the natural choice for an immutable state container is a case class:

```scala mdoc:js:shared
case class State(counter: Int = 0)
```

## Actions

State can only be updated through actions dispatched to the store.
We typically encode the set of actions as an ADT:

```scala mdoc:js:shared
enum Action:
  case Inc(amount: Int)
  case Reset
```

## Store

With the `State` and `Action` types in hand, we can set up our store,
which is a centralized place for all state updating logic.

```scala mdoc:js:shared
import cats.effect.*

object Store:

  // When all actions are pure state updates (no effects), we can use the `pure` constructor.
  def apply[F[_]](implicit
      F: Concurrent[F]
  ): Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store.pure[F, State, Action](State()):
      case (Action.Inc(amount), state) =>
        state.copy(counter = state.counter + amount)
      case (Action.Reset, state) => state.copy(counter = 0)
```

The fact that `store` is a `Resource` will turn out to be extremely useful later
(think WebSockets, subscribing to state changes, etc.).
Be sure to check out the other examples to see more elaborate store logic.

## View

Finally, we describe how our page should be rendered using the built-in DSL
for HTML markup.

```scala mdoc:js:shared
trait View:
  self: ff4s.Dsl[State, Action] =>

  val view =

    import html.*

    useState: state =>
      div(
        cls := "counter-example", // cls b/c class is a reserved keyword in scala
        div(s"value: ${state.counter}"),
        button(
          cls := "counter-button",
          "Increment",
          onClick := (_ => Some(Action.Inc(1)))
        ),
        button(
          cls := "counter-button",
          "Decrement",
          onClick := (_ => Some(Action.Inc(-1)))
        ),
        button(
          cls := "counter-button",
          "Reset",
          onClick := (_ => Some(Action.Reset))
        )
      )
```

To turn this into an app, we need a small amount of boilerplate.

```scala mdoc:js:compile-only
// App.scala
class App[F[_]](using F: Concurrent[F])
    extends ff4s.App[F, State, Action]
    with View:
  override val store = Store[F]

// Main.scala
// this defines an appropriate `main` method for us
object Main extends ff4s.IOEntryPoint(new App) // uses cats.effect.IO for F
```

```scala mdoc:js:invisible
class App[F[_]](using F: Concurrent[F])
    extends ff4s.App[F, State, Action]
    with View:
  override val store = Store[F]
  override val rootElementId = node.getAttribute("id")

new ff4s.IOEntryPoint(new App, false).main(Array())
```
