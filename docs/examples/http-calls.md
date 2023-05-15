# HTTP Calls

This example illustrates how making HTTP calls work in ff4s.

## State

In Scala, the natural choice for an immutable state container is a case class:

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

final case class State(fact: String = "")

case class Fact(text: String)
object Fact {
  implicit val codec: Codec[Fact] = deriveCodec
}

```

## Actions

State can only be updated through actions dispatched to the store.
We typically encode the set of actions as an ADT:

```scala mdoc:js:shared
sealed trait Action
case class Generate() extends Action
case class SetFact(fact: String) extends Action
```

## Store

With the `State` and `Action` types in hand, we can set up our store:

```scala mdoc:js:shared

import cats.effect._
import cats.syntax.all._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case SetFact(str) => _.copy(fact = str) -> none
        case Generate() =>
          (
            _,
            ff4s
              .HttpClient[F]
              .get[Fact](s"http://numbersapi.com/random?json")
              .flatMap { fact =>
                store.dispatch(SetFact(fact.text))
              }
              .some
          )

      }
    }

}
```

The purpose of `none` will become clear when looking at more interesting examples
involving side-effects such as fetching data from the back-end.

The fact that `store` is a `Resource` is extremely useful because it allows
us to do interesting things in the background (think WebSockets,
subscribing to state changes, etc.).
Be sure to check out the examples provided in this repo to see more elaborate
store logic, including WebSocket messaging and REST calls.

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
          cls := "m-2",
          h1("Http calls"),
          button(
            cls := "m-1 p-1",
            "generate",
            onClick := (_ => Generate().some)
          ),
          div(s"fact: ${state.fact}")
        )
      }

  }
}
```

## App

To turn this into an app all we need to do is implement the `ff4s.App`
trait using `store` and `view` from above and pass an
instance of it to the `IOEntryPoint` class, which in turn defines an
appropriate `main` method for us:

```scala mdoc:js:compile-only
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
}

object Main extends ff4s.IOEntryPoint(new App) // uses cats.effect.IO for F
```

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
