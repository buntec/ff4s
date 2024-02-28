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

package examples.example6

import cats.effect.Async
import cats.effect.implicits._
import fs2.Stream

import scala.concurrent.duration._

case class State(toggle: Boolean = true) {
  def flipToggle: State = copy(toggle = !toggle)
}

sealed trait Action

object Action {

  case class Toggle() extends Action

}

/*
 * This example was used to reproduce a bug: an earlier version of ff4s
 * mapped `class` to the `className` property, which cannot be deleted
 * so that when `class` is removed from an element it would still
 * survive the patching. Mapping `class` to the `class` attribute fixes the issue.
 * */
class App[F[_]](implicit val F: Async[F]) extends ff4s.App[F, State, Action] {

  override val store = for {

    store <- ff4s.Store.pure[F, State, Action](State()) {
      case (Action.Toggle(), state) => state.flipToggle
    }

    _ <- Stream
      .fixedDelay(3.seconds)
      .evalMap(_ => store.dispatch(Action.Toggle()))
      .compile
      .drain
      .background

  } yield store

  import html._

  override val view = useState { state =>
    div(
      cls := "flex flex-col items-center h-screen",
      span(s"Toggle: ${state.toggle}"),
      (
        if (state.toggle) div("hello", cls := "foo") else div("hello")
      )
    )

  }

}
