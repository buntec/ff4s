# Reusable Components

In this example, we illustrate how the creation of reusable UI components works using `ff4s`. Two of many,
button and select components are popular in frontend development and are going to be used in this example along with the [Frankfurter API](https://frankfurter.app).

## Components

In this example, custom select and button components are implemented as member methods of a `Components` class
with type parameters `F` for the effect type, `S` for the state type and `A` for the action type.

### Select

A select component with options of type `O` is modelled by a function with the following inputs:

1. `fromString`: converts a string option to type `O`.
2. `onChange0`: performs an action based on selected option and state.
3. `options`: list of options.
4. `selected0`: currently selected option function of the state.

### Button

Likewise for a button component inputs:

1. `onClick0`: react to click events by performing an action.
2. `isDisabled`: disable the button based on the state.

```scala mdoc:js:shared
import org.scalajs.dom
import cats.Show
import cats.kernel.Eq
import cats.syntax.all._

// Class defining all components
class Components[F[_], S, A] {

  // Custom select component
  def customSelect[O: Show: Eq](
      fromString: String => Option[O],
      onChange0: (S, O) => Option[A],
      options: List[O],
      selected0: S => O
  )(implicit dsl: ff4s.Dsl[F, S, A]): dsl.V = {
    import dsl._
    import dsl.html._
    useState { state =>
      select(
        onChange := ((ev: dom.Event) =>
          ev.target match {
            case el: dom.HTMLSelectElement =>
              fromString(el.value).flatMap(onChange0(state, _))
            case _ => None
          }
        ),
        options.map { name =>
          option(
            selected := (name === selected0(state)), // use of Eq[O] implicit
            key := name.show, // use of Show[O] implicit
            value := name.show,
            name.show
          )
        }
      )
    }

  }

  // Custom button component
  def customButton(
      dsl: ff4s.Dsl[F, S, A]
  )(child: dsl.V, onClick0: S => Option[A], isDisabled: S => Boolean): dsl.V = {
    import dsl._
    import dsl.html._

    useState { state =>
      button(
        child,
        disabled := isDisabled(state),
        onClick := (_ => onClick0(state))
      )
    }
  }

}
```

Note that an instance of `ff4s.Dsl` is passed explicitly to the `customButton` function
as the `child` argument type depends explicitly on it, contrary to the select component example where
the instance is passed as an implicit.

## State

```scala mdoc:js:shared
final case class State(
    numOfRequests: Int = 0,
    currencyPair: CurrencyPair = CurrencyPair.EURUSD,
    apiResponse: Option[ApiResponse] = None
) {

  def ccyPairOption: (String, String) =
    (
      currencyPair.show.toString.take(3),
      currencyPair.show.toString.takeRight(3)
    )

}
```

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

final case class ApiResponse(rates: Map[String, Double])
object ApiResponse {
  implicit val decoder: Decoder[ApiResponse] = deriveDecoder
}
```

In this example, the elements of `customSelect` are currency pairs. A generic currency pair is modelled by a
`CurrencyPair` trait. Specific pairs are `case object`'s extending from the latter.

```scala mdoc:js:shared
import cats.Show
import cats.kernel.Eq

sealed trait CurrencyPair

object CurrencyPair {
  // In this example, we choose few currency pairs
  case object EURUSD extends CurrencyPair
  case object USDCHF extends CurrencyPair
  case object GBPUSD extends CurrencyPair
  case object CHFEUR extends CurrencyPair

  // try to parse a string to a `CurrencyPair`
  def fromString(s: String): Option[CurrencyPair] = s match {
    case "EURUSD" => EURUSD.some
    case "USDCHF" => USDCHF.some
    case "GBPUSD" => GBPUSD.some
    case "CHFEUR" => CHFEUR.some
    case _        => none
  }

  // all available currency pairs
  val all = List(EURUSD, USDCHF, GBPUSD, CHFEUR)

  implicit val show: Show[CurrencyPair] = Show.show {
    case EURUSD => "EURUSD"
    case USDCHF => "USDCHF"
    case GBPUSD => "GBPUSD"
    case CHFEUR => "CHFEUR"
  }

  implicit val eq: Eq[CurrencyPair] = Eq.fromUniversalEquals
}
```

## Actions

```scala mdoc:js:shared
sealed trait Action
case class SetCurrencyPair(pair: CurrencyPair) extends Action
case object MakeApiRequest extends Action
case class SetApiResponse(response: Option[ApiResponse]) extends Action
```

## Store

```scala mdoc:js:shared
import cats.effect._
import cats.syntax.all._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case SetCurrencyPair(pair) => _.copy(currencyPair = pair) -> none
        case SetApiResponse(response) =>
          state =>
            state.copy(
              apiResponse = response,
              numOfRequests = state.numOfRequests + 1
            ) -> none
        case MakeApiRequest =>
          state =>
            (
              state,
              state.ccyPairOption.some.map { case (ccy1, ccy2) =>
                ff4s
                  .HttpClient[F]
                  .get[ApiResponse](
                    s"https://api.frankfurter.app/latest?from=$ccy1&to=$ccy2"
                  )
                  .flatMap(response =>
                    store.dispatch(SetApiResponse(response.some))
                  )

              }
            )
      }
    }
}
```

## View

```scala mdoc:js:shared
import cats.syntax.all._

object View {

  def apply[F[_]](implicit dsl: ff4s.Dsl[F, State, Action]) = {

    import dsl._
    import dsl.html._

    // create a `Components` instance and import components
    val components = new Components[F, State, Action]
    import components._

    useState { state =>
      div(
        customSelect[CurrencyPair](
          CurrencyPair.fromString,
          (_, pair) => SetCurrencyPair(pair).some,
          CurrencyPair.all,
          _.currencyPair
        ),
        div(s"Selected currency pair: ${state.currencyPair}"),
        customButton(dsl)(
          span("Get FX Rate"),
          _ => MakeApiRequest.some,
          _.numOfRequests == 10
        ),
        div(
          s"Fx rate: ${state.apiResponse.flatMap(_.rates.values.headOption).getOrElse("")}"
        ),
        if (state.numOfRequests == 10)
          div(
            styleAttr := "color: red",
            "Button disabled after 10 requests! Refresh page for more."
          )
        else div(s"${10 - state.numOfRequests} remaining requests!")
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
