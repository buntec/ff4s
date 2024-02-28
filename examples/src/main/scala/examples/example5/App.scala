/*
 * Copyright 2022 buntec
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package examples.example5

import cats.effect.Async
import cats.effect.implicits._
import fs2.Stream

import scala.concurrent.duration._

case class State(toggle: Boolean = true)

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

    store <- ff4s.Store[F, State, Action](State())(_ => {
      case (Action.Noop(), state) => state -> F.unit
      case (Action.Toggle(), state) =>
        state.copy(toggle = !state.toggle) -> F.unit
    })

    _ <- Stream
      .fixedDelay(3.seconds)
      .evalMap(_ => store.dispatch(Action.Toggle()))
      .compile
      .drain
      .background

  } yield store

  import html._

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
