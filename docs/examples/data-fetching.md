# Data Fetching

Fetching data from the back-end is probably the most common type of IO in single-page applications.
In this example we illustrate this pattern in `ff4s` using a simple HTTP GET
request to the [frankfurter API](https://frankfurter.app), which returns foreign exchange rates for a currency pair we supply.

## State

```scala mdoc:js:shared
final case class State(
    pair: String = "EUR/USD",
    exchangeRate: Option[ExchangeRate] = None
) {
  def baseCurrency: String = pair.split("/").headOption.getOrElse("EUR")
  def quoteCurrency: String = pair.split("/").tail.headOption.getOrElse("USD")
}
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
case class SetCurrencyPair(pair: String) extends Action
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
        case SetExchangeRate(rate) => _.copy(exchangeRate = rate) -> none
        case SetCurrencyPair(pair) => _.copy(pair = pair) -> none
        case GetExchangeRate =>
          state =>
            (
              state,
              ff4s
                .HttpClient[F]
                .get[ExchangeRate](
                  s"https://api.frankfurter.app/latest?from=${state.baseCurrency}&to=${state.quoteCurrency}"
                )
                .flatMap(rate => store.dispatch(SetExchangeRate(rate.some)))
                .some
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
          value := state.pair,
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                SetCurrencyPair(el.value).some
              case _ => None
            }
          )
        ),
        button(
          "New fact",
          onClick := (_ => GetExchangeRate.some)
        ),
        div(
          s"${state.baseCurrency}/${state.quoteCurrency}: ${state.exchangeRate.flatMap(_.rates.get(state.quoteCurrency)).getOrElse("")}"
        )
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
