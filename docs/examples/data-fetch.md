# Data Fetch

This example illustrates how fetching data via HTTP calls work in `ff4s`. A random fact about a number is generated
on each button click using the [numbers API](http://numbersapi.com/#42).

## State

```scala mdoc:js:shared
final case class State(number: Int = 0, fact: Option[Fact] = None)
```

The random fact is also naturally modelled by a case class with a `circe` codec:

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
import cats.effect._
import cats.syntax.all._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { store =>
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

}
```

The `SetFact` and `SetNumber` actions are only responsible for updating the state hence the purpose of the `none`. However more interestingly,
the `Generate` action is performing a `GET` request that is conceived as a 'long running' effect and hence is scheduled on a separate fiber.
This is indeed handled internally by `ff4s` in order to avoid a blocking HTTP call.

After making the call, the state is updated
with a random fact through the `dispatch` method of the store that returns an `F[Unit]`. Note that the effect is optional hence the presence of the `.some`.

## View

```scala mdoc:js:shared
object View {

  def apply[F[_]](implicit dsl: ff4s.Dsl[F, State, Action]) = {

    import dsl._
    import dsl.html._
    import org.scalajs.dom

    useState { state =>
      div(
        h1("Data Fetch"),
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
        button(
          "New fact",
          onClick := (_ => Generate().some)
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