# Subscriptions

The fact that the store in ff4s is a `Resource` and that `store.state` is a `Signal` allows us to subscribe to updates of (part of) the state and react to them. A common use case is debouncing API calls based on user input.

## State

```scala mdoc:js:shared
final case class State(
    userInput: Option[String] = None,
    exchangeRate: Option[ExchangeRate] = None,
    error: Option[String] = None
)
```

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

case class ExchangeRate(rates: Map[String, Double])
object ExchangeRate {
  implicit val decoder: Decoder[ExchangeRate] = deriveDecoder
}
```

## Actions

```scala mdoc:js:shared
sealed trait Action
case class GetExchangeRate(base: String, quote: String) extends Action
case class SetExchangeRate(exchangeRate: Option[ExchangeRate]) extends Action
case class SetUserInput(userInput: Option[String]) extends Action
case class SetError(error: Option[String]) extends Action
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
          case SetExchangeRate(rate) =>
            _.copy(exchangeRate = rate, error = None) -> none
          case SetUserInput(userInput) => _.copy(userInput = userInput) -> none
          case SetError(error)         => _.copy(error = error) -> none
          case GetExchangeRate(base: String, quote: String) =>
            state =>
              (
                state,
                ff4s
                  .HttpClient[F]
                  .get[ExchangeRate](
                    s"https://api.frankfurter.app/latest?from=$base&to=$quote"
                  )
                  .flatMap { rate =>
                    store.dispatch(SetExchangeRate(rate.some))
                  }.
                  handleErrorWith{ _ =>store.dispatch(SetError("Failed to get rate!".some)) }
                  .some
              )

        }
      }
      .flatTap {
        // subscribe to changes in user input and trigger debounced API calls
        store =>
          store.state
            .map(_.userInput)
            .discrete
            .changes
            .debounce(1.second)
            .map {
              _.flatMap { userInput =>
                (
                  userInput.split("/").headOption,
                  userInput.split("/").tail.headOption
                ).tupled
              }
            }
            .unNone
            .evalMap { case (base, quote) =>
              store.dispatch(GetExchangeRate(base, quote))
            }
            .compile
            .drain
            .background
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
        h1("Subscriptions"),
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

Implementation of `ff4s.App` and `ff4s.IOEntryPoint` is straightforward and omitted for brevity.

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
