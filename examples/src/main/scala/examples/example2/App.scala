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

package examples.example2

import cats.effect.Concurrent
import cats.syntax.all._
import monocle.syntax.all._

// This example shows how to create and use reusable components.
class App[F[_]](implicit val F: Concurrent[F])
    extends ff4s.App[F, State, Action]
    with Components[State, Action] {

  private val unit = Concurrent[F].unit

  override val store = ff4s.Store[F, State, Action](State()) { _ =>
    (_, _) match {
      case (Action.SetWeekday(weekday), state) =>
        state.focus(_.weekday).replace(weekday) -> unit

      case (Action.Inc(), state) =>
        state.focus(_.counter).modify(_ + 1) -> unit

      case (Action.Dec(), state) => state.focus(_.counter).modify(_ - 1) -> unit
    }
  }

  import html._

  // build our app using the imported components
  override val view = useState { state =>
    pageWithHeaderAndFooter("ff4s Reusable Components")(
      div(
        cls := "flex flex-col items-center",
        fancyWrapper("A simple counter")(
          counter(_.counter, Action.Inc(), Action.Dec())
        ),
        fancyWrapper("A drop-down select")(
          labeledSelect[Weekday](
            "Weekday",
            Weekday.values,
            state.weekday,
            l => Action.SetWeekday(l).some
          ),
          span(s"Your selection: ${state.weekday.show}")
        )
      )
    )
  }

}
