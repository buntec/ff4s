# Reusable Components

In this example, we illustrate how the creation of reusable UI components works using `ff4s`. Two of many,
a button and dropdown components are crucial in frontend development and hence we are going to use them
in this example along with the [numbers API](http://numbersapi.com/).

## Dropdown List Component

A dropdown list component can be modelled as follows. Note that `F[_]` encodes the effect type, `A` for action, `S` for state and `O` encodes
the type of elements of the dropdown list.

```scala mdoc:js:shared
import org.scalajs.dom
import cats.std.Eq
import cats.std.Show

def dropdown[F[_], A, S, O: Show: Eq](
  fromString : String => Option[O],
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
            cls := "p-2",
            selected := (name === selected0(state)),
            key := name.show,
            value := name.show,
            name.show
          )
        }
      )
    }

}
```

## Button Component

A button component can be modelled as follows:

```scala mdoc:js:shared
def btn[F[_], A, S](dsl: ff4s.Dsl[F, A, S])(child:
        dsl.V, onClick0: S => Option[A],
        isDisabled: S => Boolean): dsl.V = {
            import dsl._
            import dsl.html._

            useState { state =>
             button(
              dsl.V,
              disabled := isDisabled(state),
              onClick :=  (_ => onClick0(state))
            )
}
}
```

## State

```scala mdoc:js:shared
final case class State(
    buttonClicks: Int = 0,
    currencyPair: [CurrencyPair] = CurrencyPair.EURUSD,
    apiResponse: Option[FxRate] = None
)
```

```scala mdoc:js:shared
final case class FxRate(rates: Map[String, Double])
```

In this example, the dropdown list is a list of currency pairs. A generic currency pair is modelled by a
`CurrencyPair` trait. Specific pairs are `case object`s extending from the latter.

```scala mdoc:js:shared

sealed trait CurrencyPair

object CurrencyPair {
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
    case _ => none
    }

    // all available currency pairs
    val all = List(EURUSD, USDCHF, GBPUSD, CHFEUR)

    implicit val show: Show[CurrencyPair] = Show.of(_.toString)

    implicit val eq: Eq[CurrencyPair] = Eq.fromUniversalEq
}
```

## Actions

```scala mdoc:js:shared
sealed trait Action
case object IncClicks extends Action
case class SetCurrencyPair(pair: CurrencyPair) extends Action
case object MakeApiRequest extends Action
case object SetApiResponse(response: Option[FxRate]) extends Action
```

## Store

```scala mdoc:js:shared
import cats.effect._
import cats.syntax.all._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case IncClicks     => state => state.copy(buttonClicks = state.buttonClicks + 1) -> none
        case SetCurrencyPair(pair) => _.copy(pair = pair) -> none
        case SetApiResponse(response) => _.copy(apiResponse = response.some) -> none
        case MakeApiRequest =>
          state =>
            (
              state,
              ff4s
                .HttpClient[F]
                .get[FxRate](s"http://numbersapi.com/${state.number}?json")
                .flatMap(rate => store.dispatch(SetApiResponse(rate.some)))
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
    div ()

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
