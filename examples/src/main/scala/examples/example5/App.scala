package examples.example5

import cats.effect.Async
import cats.effect.implicits._
import cats.syntax.all._
import fs2.Stream
import scala.concurrent.duration._

case class State(
    foo: Int = 17,
    toggle: Boolean = true
)

sealed trait Action

object Action {

  case class Noop() extends Action
  case class Toggle() extends Action

}

// This example was used to reproduce a bug:
//
// A previous version of ff4s mapped reflected attributes to
// props (via scala-dom-types), which is problematic b/c snappdom cannot delete props
// when patching a node so things like `id` can erroneously
// survive a patch. This example demonstrates this behavior.
// Mapping reflected attributes to attributes (by changing scala-dom-types code-gen config)
// fixes the issue.

class App[F[_]](implicit val F: Async[F]) extends ff4s.App[F, State, Action] {

  override val store = for {

    store <- ff4s.Store[F, State, Action](State())(_ =>
      _ match {
        case Action.Noop() => (_, none)
        case Action.Toggle() =>
          state => state.copy(toggle = !state.toggle) -> none
      }
    )

    _ <- Stream
      .fixedDelay(3.seconds)
      .evalMap(_ => store.dispatch(Action.Toggle()))
      .compile
      .drain
      .background

  } yield store

  import dsl._
  import dsl.html._

  val heading = h1(cls := "m-4 text-4xl", "Example")

  override val view = useState { state =>
    div(
      cls := "flex flex-col items-center h-screen",
      heading,
      (if (state.toggle) div("hello") else empty),
      div(
        idAttr := "bar",
        div(
          "blah"
        )
      ),
      div(
        "foo",
        idAttr := "foo",
        div(
          "baz",
          idAttr := "baz"
        )
      )
    )

  }

}
