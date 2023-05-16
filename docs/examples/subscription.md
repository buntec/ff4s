# Subscription

This example illustrates how subscribing to state changes work in `ff4s`. A random fact is generated
on user input using the [numbers API](http://numbersapi.com/#42).

## State

```scala mdoc:js:shared
final case class State(number: Int = 0, fact: Option[Fact] = None)
```

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

case class Fact(text: String)
object Fact {
  implicit val codec: Codec[Fact] = deriveCodec
}
```

## Actions

```scala mdoc:js:shared
sealed trait Action
// Generates a fact by making a GET request
case class Generate() extends Action
// Updates the state with the given fact
case class SetFact(fact: Option[Fact]) extends Action
// Updates the state with the given number
case class SetNumber(number: Int) extends Action
```

## Store

```scala mdoc:js:shared
import cats.syntax.all._
import cats.effect._
import cats.effect.implicits._
import scala.concurrent.duration._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s
      .Store[F, State, Action](State()) { store =>
        _ match {
          case SetFact(fact)     => _.copy(fact = fact) -> none
          case SetNumber(number) => _.copy(number = number) -> none
          case Generate() =>
            state =>
              (
                state,
                ff4s
                  .HttpClient[F]
                  .get[Fact](s"http://numbersapi.com/${state.number}?json")
                  .flatMap { fact =>
                    store.dispatch(SetFact(fact.some))
                  }
                  .some
              )

        }
      }
      .flatTap { store =>
        store.state
          .map(_.number)
          .discrete
          .changes
          .debounce(3.seconds)
          .evalMap { _ => store.dispatch(Generate()) }
          .compile
          .drain
          .background
      }

}
```

The fact that the `ff4s.Store[F, State, Action]` is a resource allows us interestingly to subscribe to state changes in the background
while the store is in use. In fact, the state can be accessed from the store as a `fs2.Signal[F, State]` and mapped to a corresponding element
of interest of the state. The methods `.discrete.changes` are then responsible to watch changes in the corresponding state (or element of the state).

Note the interesting `.debounce` method that limits the amount of effects evaluation to one evaluation at most per 3 seconds. This particularly
useful to avoid proliferation of evaluations if the state changes with high frequency.

## View

```scala mdoc:js:shared
object View {

  def apply[F[_]](implicit dsl: ff4s.Dsl[F, State, Action]) = {

    import dsl._
    import dsl.html._
    import org.scalajs.dom

    useState { state =>
      div(
        h1("Subscription"),
        input(
          tpe := "number",
          value := state.number.toString,
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                SetNumber(el.value.toIntOption.getOrElse(0)).some
              case _ => None
            }
          )
        ),
        div(s"${state.fact.map(_.text).getOrElse("")}")
      )
    }

  }
}
```

## App

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
