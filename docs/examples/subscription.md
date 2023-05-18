# Subscriptions

The fact that the store in ff4s is a `Resource` and that `store.state` is a `Signal` allows us to subscribe to updates of (part of) the state and react to them. A common use case is debouncing API calls based on user input. 

## State

```scala mdoc:js:shared
final case class State(number: Int = 0, fact: Option[Fact] = None)
```

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

case class Fact(text: String)
object Fact {
  implicit val codec: Decoder[Fact] = deriveDecoder
}
```

## Actions

```scala mdoc:js:shared
sealed trait Action
case object GetRandomFact extends Action
case class SetFact(fact: Option[Fact]) extends Action
case class SetNumber(number: Int) extends Action
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
          case SetFact(fact)     => _.copy(fact = fact) -> none
          case SetNumber(number) => _.copy(number = number) -> none
          case GetRandomFact =>
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
      .flatTap {
        // subscribe to changes in user input and trigger debounced API calls
        _.state
          .map(_.number)
          .discrete
          .changes
          .debounce(1.second)
          .evalMap(_ => store.dispatch(GetRandomFact))
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
        h1("Subscription"),
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
        div(s"${state.fact.map(_.text).getOrElse("")}")
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
