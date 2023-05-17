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
  implicit val codec: Codec[Fact] = deriveCodec
}
```

## Actions

```scala mdoc:js:shared
sealed trait Action
case object GetRandomFact extends Action
case class SetFact(fact: Option[Fact]) extends Action
case class SetNumber(number: Int) extends Action
case object Cancel extends Action

## Store

```scala mdoc:js:shared
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import cats.effect.std.Supervisor
import cats.effect.std.MapRef
import scala.concurrent.duration._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] = for {

    supervisor <- Supervisor[F]

    fibers <- MapRef
      .ofSingleImmutableMap[F, String, Fiber[F, Throwable, Unit]]()
      .toResource

    store <- ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case SetFact(fact)     => _.copy(fact = fact) -> none
        case SetNumber(number) => _.copy(number = number) -> none
        case Cancel(cancelKey) =>
          (_, fibers(cancelKey).get.flatMap(_.foldMapM(_.cancel)).some)
        case Generate(cancelKey) =>
          state =>
            (
              state,
              supervisor
                .supervise(
                  Async[F].sleep(3.seconds) *>
                    ff4s
                      .HttpClient[F]
                      .get[Fact](s"http://numbersapi.com/${state.number}?json")
                      .flatMap { fact =>
                        store.dispatch(SetFact(fact.some))
                      }
                )
                .flatMap { fiber =>
                  fibers
                    .getAndSetKeyValue(cancelKey, fiber)
                    .flatMap(_.foldMapM(_.cancel))
                }
                .some
            )

      }
    }
  } yield store
}
```

A `Supervisor[F]` is used to run an effect `F` on a fiber. We get for free a handler on each supervised fiber that will allow us to control
its cancellation. This particularly beneficial as we store all fibers in a `MapRef` with a corresponding cancellation key.

Note the `Cancel` action takes a cancellation key `cancelKey` that when dispatched, gets the fiber from the fibers `MapRef` and attemps to cancel it.
In addition to that, the `Generate` action attempts to cancel the effect if running before executing the effect itself. This particularly useful when users
click multiple times on a button that fetches data for example. This would then avoid erroneous behavior.

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
                SetNumber(el.value.toIntOption.getOrElse(0)).some
              case _ => None
            }
          )
        ),
        button(
          "New fact",
          onClick := (_ => Generate("number").some)
        ),
        button("Cancel", onClick := (_ => Cancel("number").some)),
        div(s"${state.fact.map(_.text).getOrElse("")}")
      )
    }

  }
}
```

## App

```scala mdoc:js:compile-only
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
}

object Main extends ff4s.IOEntryPoint(new App) // uses cats.effect.IO for F
```

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
