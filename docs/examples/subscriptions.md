# Subscriptions

The fact that the store in ff4s is a `Resource` and that `store.state` is a `Signal` allows us to subscribe to updates of (part of) the state and react to them. A common use case is debouncing API calls based on user input.

## State

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

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

case class ApiResponse(rates: Map[String, Double])
object ApiResponse {
  implicit val decoder: Decoder[ApiResponse] = deriveDecoder
}
```

## Actions

```scala mdoc:js:shared
sealed trait Action
case class SetUserInput(userInput: Option[String]) extends Action
case class MakeApiRequest(ccy1: String, ccy2: String) extends Action
case class SetApiResponse(response: Option[ApiResponse]) extends Action
case class SetErrorMessage(msg: Option[String]) extends Action
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
          case SetApiResponse(response) =>
            _.copy(apiResponse = response, errorMessage = None) -> none
          case SetUserInput(userInput) => _.copy(userInput = userInput) -> none
          case SetErrorMessage(msg)    => _.copy(errorMessage = msg) -> none
          case MakeApiRequest(ccy1: String, ccy2: String) =>
            state =>
              (
                state,
                ff4s
                  .HttpClient[F]
                  .get[ApiResponse](
                    s"https://api.frankfurter.app/latest?from=$ccy1&to=$ccy2"
                  )
                  .flatMap { response =>
                    store.dispatch(SetApiResponse(response.some))
                  }
                  .handleErrorWith { t =>
                    store.dispatch(
                      SetErrorMessage(s"Failed to get FX rate: $t".some)
                    )
                  }
                  .some
              )

        }
      }
      .flatTap {
        // subscribe to changes in user input and trigger debounced API calls
        store =>
          store.state
            .map(_.ccyPairOption)
            .discrete
            .changes
            .debounce(1.second)
            .evalMap {
              case Some((ccy1, ccy2)) =>
                store.dispatch(MakeApiRequest(ccy1, ccy2))
              case None => store.dispatch(SetApiResponse(None))
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

Implementation of `ff4s.App` and `ff4s.IOEntryPoint` is straightforward and omitted for brevity.

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
