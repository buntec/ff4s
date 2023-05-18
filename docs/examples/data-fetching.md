# Data Fetching

Fetching data from the back-end is probably the most common type of IO
in single-page applications. In this example we illustrate this pattern
using a simple HTTP GET request to the [Frankfurter API](https://frankfurter.app),
which provides foreign exchange rates published by the European Central Bank.

## State

The state needs to hold the user's input, the exchange rate returned by the API,
and possibly an error message in case something goes wrong.
We also add a convenience method for parsing currency pairs from user input.

```scala mdoc:js:shared
final case class State(
    userInput: Option[String] = None,
    apiResponse: Option[ApiResponse] = None,
    errorMessage: Option[String] = None
) {

  def ccyPairOption: Option[(String, String)] =
    userInput.flatMap {
      _ match {
        case Utils.ccyPairPattern(ccy1, ccy2) => Some((ccy1, ccy2))
        case _                                => None
      }
    }

}

object Utils {

  val ccyPairPattern = """([a-zA-Z]{3})/?([a-zA-Z]{3})""".r

}
```

As per usual, we model the JSON API response using a case class with a
derived [circe](https://circe.github.io/circe/) decoder:

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

case class ApiResponse(rates: Map[String, Double])
object ApiResponse {
  implicit val decoder: Decoder[ApiResponse] = deriveDecoder
}
```

## Actions

The action encoding is straightforward.

```scala mdoc:js:shared
sealed trait Action
case class SetUserInput(input: Option[String]) extends Action
case object MakeApiRequest extends Action
case class SetApiResponse(response: Option[ApiResponse]) extends Action
case class SetErrorMessage(msg: Option[String]) extends Action
```

## Store

The interesting bit in the store is the handling of `MakeApiRequest`.
Note how we retrieve the currency pair from the state and how we
are updating the state with the response using `store.dispatch`.

```scala mdoc:js:shared
import cats.effect._
import cats.syntax.all._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case SetApiResponse(response) =>
          _.copy(apiResponse = response, errorMessage = None) -> none
        case SetUserInput(input)  => _.copy(userInput = input) -> none
        case SetErrorMessage(msg) => _.copy(errorMessage = msg) -> none
        case MakeApiRequest =>
          state =>
            (
              state,
              state.ccyPairOption.map { case (ccy1, ccy2) =>
                ff4s
                  .HttpClient[F]
                  .get[ApiResponse](
                    s"https://api.frankfurter.app/latest?from=$ccy1&to=$ccy2"
                  )
                  .flatMap(response =>
                    store.dispatch(SetApiResponse(response.some))
                  )
                  .handleErrorWith(t =>
                    store.dispatch(
                      SetErrorMessage(s"Failed to get FX rate: $t".some)
                    )
                  )
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
        input(
          tpe := "text",
          placeholder := "e.g. EUR/USD or EURUSD",
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                SetUserInput(el.value.some).some
              case _ => None
            }
          )
        ),
        button(
          "Get FX Rate",
          onClick := (_ => MakeApiRequest.some),
          disabled := state.ccyPairOption.isEmpty
        ),
        state.errorMessage match {
          case Some(errorMsg) => div(styleAttr := "color: red", errorMsg)
          case None =>
            div(
              s"${state.apiResponse.flatMap(_.rates.values.toList.headOption).getOrElse("")}"
            )
        }
      )
    }
  }

}
```

## App

The boilerplate for `ff4s.App` and `ff4s.IOEntryPoint` is omitted.

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
