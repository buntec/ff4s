# Data Fetching

Fetching data from the back-end is probably the most common type of IO in single-page applications.
In this example we illustrate this pattern in `ff4s` using a simple HTTP GET
request to the [frankfurter API](https://frankfurter.app), which returns foreign exchange rates for a currency pair we supply.

## State

```scala mdoc:js:shared
final case class State(
    userInput: Option[String] = None,
    exchangeRate: Option[ExchangeRate] = None,
    error: Option[String] = None
)
```

We model the JSON API response using a case class with a derived [circe](https://circe.github.io/circe/) decoder:

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

case class ExchangeRate(rates: Map[String, Double])
object ExchangeRate {
  implicit val decoder: Decoder[ExchangeRate] = deriveDecoder
}
```

## Actions

The action encoding is straightforward.

```scala mdoc:js:shared
sealed trait Action
case object GetExchangeRate extends Action
case class SetExchangeRate(exchangeRate: Option[ExchangeRate]) extends Action
case class SetUserInput(userInput: Option[String]) extends Action
case class SetError(error: Option[String]) extends Action
```

## Store

The only interesting bit in the store is the handling of `GetExchangeRate`. Note how we retrieve the currency pair from the state and how we are updating the state with the retrieved rate using `store.dispatch`. A more realistic example would include error handling of failed requests.

```scala mdoc:js:shared
import cats.effect._
import cats.syntax.all._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case SetExchangeRate(rate) =>
          _.copy(exchangeRate = rate, error = None) -> none
        case SetUserInput(userInput) => _.copy(userInput = userInput) -> none
        case SetError(error)         => _.copy(error = error) -> none
        case GetExchangeRate =>
          state =>
            (
              state,
              state.userInput
                .flatMap { input =>
                  (
                    input.split("/").headOption,
                    input.split("/").tail.headOption
                  ).tupled
                }
                .map { case (base, quote) =>
                  ff4s
                    .HttpClient[F]
                    .get[ExchangeRate](
                      s"https://api.frankfurter.app/latest?from=$base&to=$quote"
                    )
                    .flatMap(rate => store.dispatch(SetExchangeRate(rate.some)))
                    .handleErrorWith { _ =>
                      store.dispatch(SetError("Failed to get rate!".some))
                    }
                }
            )
      }
    }
}
```

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
          tpe := "text",
          placeholder := "CHF/USD",
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                SetUserInput(el.value.some).some
              case _ => None
            }
          )
        ),
        button(
          "Exchange rate",
          onClick := (_ => GetExchangeRate.some)
        ),
        state.error match {
          case Some(error) => div(error)
          case None =>
            div(
              s"${state.exchangeRate.flatMap(_.rates.values.toList.headOption).getOrElse("")}"
            )
        }
      )
    }
  }

}
```

## App

The construction of `ff4s.App` and `ff4s.IOEntryPoint` is straightforward and omitted for brevity.

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
