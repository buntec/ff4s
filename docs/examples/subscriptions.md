# Subscriptions

The fact that the store is a `Resource` and that `store.state` is a `Signal`
allows us to subscribe to updates of (part of) the state and react to them.
A common use case is debouncing API calls based on user input.

## State

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

lazy  val ccyPairPattern = """([a-zA-Z]{3})/?([a-zA-Z]{3})""".r

```

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
  case MakeApiRequest(ccy1: String, ccy2: String)
  case SetApiResponse(response: Option[ApiResponse])
  case SetErrorMessage(msg: Option[String])
  case SetUserInput(userInput: Option[String])
```

## Store

```scala mdoc:js:shared
import cats.effect.*
import cats.effect.implicits.*
import scala.concurrent.duration.*
import cats.syntax.all.*

object Store:

  def apply[F[_]](implicit
      F: Async[F]
  ): Resource[F, ff4s.Store[F, State, Action]] =
      ff4s.Store[F, State, Action](State()): store =>
        case (Action.SetApiResponse(response), state) =>
            state.copy(apiResponse = response, errorMessage = None) -> F.unit
        case (Action.SetUserInput(userInput), state) =>
            state.copy(userInput = userInput) -> F.unit
        case (Action.SetErrorMessage(msg), state) =>
            state.copy(errorMessage = msg) -> F.unit
        case (Action.MakeApiRequest(ccy1: String, ccy2: String), state) =>
        (
            state,
            ff4s
            .HttpClient[F]
            .get[ApiResponse](
                s"https://api.frankfurter.app/latest?from=$ccy1&to=$ccy2"
            )
            .flatMap ( response =>
                store.dispatch(Action.SetApiResponse(response.some))
            )
            .handleErrorWith ( t =>
                store.dispatch(
                Action.SetErrorMessage(s"Failed to get FX rate: $t".some)
                )
            )
        )
      .flatTap:
        // subscribe to changes in user input and trigger debounced API calls
        store =>
          store.state
            .map(_.ccyPairOption)
            .discrete
            .changes
            .debounce(1.second)
            .evalMap:
              case Some((ccy1, ccy2)) => store.dispatch(Action.MakeApiRequest(ccy1, ccy2))
              case None => store.dispatch(Action.SetApiResponse(None))
            .compile
            .drain
            .background

```

## View

```scala mdoc:js:shared
trait View:
  self: ff4s.Dsl[State, Action] =>

  import html.*
  import org.scalajs.dom

  val view = 
    useState( state =>
      div(
        input(
          tpe := "text",
          placeholder := "e.g. EUR/USD or EURUSD",
          onInput := ((ev: dom.Event) =>
            val target = ev.target.asInstanceOf[dom.HTMLInputElement]
            Action.SetUserInput(target.value.some).some
          )
        ),
        state.errorMessage match
          case Some(errorMsg) => div(styleAttr := "color: red", errorMsg)
          case None =>
            div(
              s"${state.apiResponse.flatMap(_.rates.values.toList.headOption).getOrElse("")}"
            )
      )
    )

```

## App

Implementation of `ff4s.App` and `ff4s.IOEntryPoint` is straightforward and omitted.

```scala mdoc:js:invisible
class App[F[_]](using F: Async[F]) extends ff4s.App[F, State, Action] with View:
  override val store = Store[F]
  override val rootElementId = node.getAttribute("id")

new ff4s.IOEntryPoint(new App, false).main(Array())
```
