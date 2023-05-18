# Cancellation

This example shows how long-running effects can be made cancellable, either by triggering the same effect again before it has completed, or explicitly by clicking a cancel button. We illustrate this with a simple HTTP GET request to the [numbers API](http://numbersapi.com/) (pretending that it is long-running by adding a `F.sleep(...)`).

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
case object Cancel extends Action
```

## Store

The interesting bit is in the store. We use a `Supervisor` to fork safely the long-running effect on a new fiber. The fiber of the running effect is held in a `Ref`, where we can retrieve it for cancellation at any time.

```scala mdoc:js:shared
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import cats.effect.std.Supervisor
import scala.concurrent.duration._

object Store {

  def apply[F[_]](implicit
      F: Async[F]
  ): Resource[F, ff4s.Store[F, State, Action]] = for {

    supervisor <- Supervisor[F]

    // Since there is at most one running effect, a single `Ref` suffices.
    // If there could be more than one cancellable action at a time, we would use something
    // like `MapRef` and index the fibers by the action type or a cancellation token.
    fiber <- F.ref[Option[Fiber[F, Throwable, Unit]]](None).toResource

    store <- ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case SetFact(fact)     => _.copy(fact = fact) -> none
        case SetNumber(number) => _.copy(number = number) -> none
        case Cancel => (_, fiber.get.flatMap(_.foldMapM(_.cancel)).some)
        case GetRandomFact =>
          state =>
            (
              state,
              supervisor
                .supervise(
                  F.sleep(3.seconds) *> // pretend that this is long running
                    ff4s
                      .HttpClient[F]
                      .get[Fact](s"http://numbersapi.com/${state.number}?json")
                      .flatMap(fact => store.dispatch(SetFact(fact.some)))
                )
                .flatMap { fib =>
                  fiber
                    .getAndSet(fib.some)
                    .flatMap(
                      _.foldMapM(_.cancel)
                    ) // cancel running request, if any, and store fiber of new request
                }
                .some
            )

      }
    }
  } yield store
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
        h1("Cancellation"),
        input(
          tpe := "number",
          value := state.number.toString,
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                // A more realistic example would include input validation. Here we simply fall back to `0`.
                SetNumber(el.value.toIntOption.getOrElse(0)).some
              case _ => None
            }
          )
        ),
        button(
          "New fact",
          onClick := (_ => GetRandomFact.some)
        ),
        button("Cancel", onClick := (_ => Cancel.some)),
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
