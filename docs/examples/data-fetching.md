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
case class State(
    userInput: Option[String] = None,
    apiResponse: Option[ApiResponse] = None,
    errorMessage: Option[String] = None
):
  def ccyPairOption: Option[(String, String)] =
    userInput.flatMap:
      case ccyPairPattern(ccy1, ccy2) => Some((ccy1, ccy2))
      case _                          => None

lazy val ccyPairPattern = """([a-zA-Z]{3})/?([a-zA-Z]{3})""".r
```

As per usual, we model the JSON API response using a case class with a
derived [circe](https://circe.github.io/circe/) decoder:

```scala mdoc:js:shared
import io.circe.*
import io.circe.generic.semiauto.*

case class ApiResponse(rates: Map[String, Double])

object ApiResponse:
  given Decoder[ApiResponse] = deriveDecoder
```

## Actions

```scala mdoc:js:shared
enum Action:
  case MakeApiRequest
  case SetApiResponse(response: Option[ApiResponse])
  case SetErrorMessage(msg: Option[String])
  case SetUserInput(input: Option[String])
```

## Store

The interesting bit is the handling of `MakeApiRequest`.
Note how we retrieve the currency pair from the state and how we
are updating the state with the response using `store.dispatch`.

```scala mdoc:js:shared
import cats.effect.*
import cats.syntax.all.*

object Store:

  def apply[F[_]](using
      F: Async[F]
  ): Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()): store =>
      case (Action.SetApiResponse(response), state) =>
        state.copy(apiResponse = response, errorMessage = None) -> F.unit
      case (Action.SetUserInput(input), state) =>
        state.copy(userInput = input) -> F.unit
      case (Action.SetErrorMessage(msg), state) =>
        state.copy(errorMessage = msg) -> F.unit
      case (Action.MakeApiRequest, state) =>
        (
          state,
          state.ccyPairOption.foldMapM: (ccy1, ccy2) =>
            ff4s
              .HttpClient[F]
              .get[ApiResponse](
                s"https://api.frankfurter.app/latest?from=$ccy1&to=$ccy2"
              )
              .flatMap(response =>
                store.dispatch(Action.SetApiResponse(response.some))
              )
              .handleErrorWith(t =>
                store.dispatch(
                  Action.SetErrorMessage(s"Failed to get FX rate: $t".some)
                )
              )
        )
```

## View

```scala mdoc:js:shared
trait View:
  self: ff4s.Dsl[State, Action] =>

  import html.*
  import org.scalajs.dom

  val view =
    useState: state =>
      div(
        input(
          tpe := "text",
          placeholder := "e.g. EUR/USD or EURUSD",
          onInput := ((ev: dom.Event) =>
            // casting is sometimes necessary when `org.scalajs.dom` doesn't give us precise enough types.
            val target = ev.target.asInstanceOf[dom.HTMLInputElement]
            Action.SetUserInput(target.value.some).some
          )
        ),
        button(
          "Get FX Rate",
          onClick := (_ => Action.MakeApiRequest.some),
          disabled := state.ccyPairOption.isEmpty
        ),
        state.errorMessage match
          case Some(errorMsg) => div(styleAttr := "color: red", errorMsg)
          case None =>
            div(
              s"${state.apiResponse.flatMap(_.rates.values.toList.headOption).getOrElse("")}"
            )
      )
```

## App

The boilerplate for `ff4s.App` and `ff4s.IOEntryPoint` is omitted.

```scala mdoc:js:invisible
class App[F[_]](using F: Async[F]) extends ff4s.App[F, State, Action] with View:
  override val store = Store[F]
  override val rootElementId = node.getAttribute("id")

new ff4s.IOEntryPoint(new App, false).main(Array())
```
