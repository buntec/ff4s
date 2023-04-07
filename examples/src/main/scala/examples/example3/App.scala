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

package examples.example3

import cats.effect.Concurrent
import cats.syntax.all._

import monocle.syntax.all._

// This example shows how to create and use reusable components.
class App[F[_]](implicit val F: Concurrent[F])
    extends ff4s.App[F, State, Action] {

  val store = ff4s.Store[F, State, Action](State()) { ref =>
    _ match {
      case Action.SetWeekday(weekday) =>
        ref.update(_.focus(_.weekday).replace(weekday))
      case Action.Inc() =>
        ref.update(_.focus(_.counter).modify(_ + 1))
      case Action.Dec() =>
        ref.update(_.focus(_.counter).modify(_ - 1))
    }
  }

  // instantiate components with concrete State and Action types
  val components = new Components[F, State, Action]
  import components._

  import dsl._
  import dsl.syntax.html._

  // build our app using the imported components
  val root = useState { state =>
    pageWithHeaderAndFooter(dsl)("ff4s Reusable Components")(
      div(
        cls := "flex flex-col items-center",
        fancyWrapper(dsl)("A simple counter")(
          counter(_.counter, _ => Action.Inc(), _ => Action.Dec())
        ),
        fancyWrapper(dsl)("A drop-down select")(
          labeledSelect[Weekday](
            "Weekday",
            (s: String) => Weekday.fromString(s),
            (_, l) => Action.SetWeekday(l).some,
            Weekday.values,
            _.weekday
          ),
          span(s"Your selection: ${state.weekday.show}")
        )
      )
    )
  }

}
