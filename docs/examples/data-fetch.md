# Data Fetching

Fetching data from the back-end is probably the most common type of IO in single-page applications.
In this example we illustrate this pattern in `ff4s` using a simple HTTP GET 
request to the [numbers API](http://numbersapi.com/), which returns a random fact
about any number that we supply.

## State

```scala mdoc:js:shared
final case class State(number: Int = 0, fact: Option[Fact] = None)
```

We model the JSON API response using a case class with a derived [circe](https://circe.github.io/circe/) decoder:

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

case class Fact(text: String)
object Fact {
  implicit val decoder: Decoder[Fact] = deriveDecoder
}
```

## Actions

The action encoding is straightforward.

```scala mdoc:js:shared
sealed trait Action
case object GetRandomFact extends Action
case class SetFact(fact: Option[Fact]) extends Action
case class SetNumber(number: Int) extends Action
The only interesting bit in the store is the handling of `GetRandomFact`. Note how we retrieve the number from the state and how we are updating the state with the retrieved fact using `store.dispatch`. A more realistic example would include error handling of failed requests.
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

The construction of `ff4s.App` and `ff4s.IOEntryPoint` is straightforward and omitted for brevity.

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
