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

package ff4s.examples.example3

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import ff4s.Store

// This example demonstrates how we can integrate reusable components using `embed`.

trait ReusableStuff[F[_]] {

  // constant means no state and no actions
  val dsl = ff4s.Dsl.constant[F]

  import dsl.syntax.html._

  val header = div(cls := "p-1 bg-indigo-300", "Hello!")

  val footer = div(cls := "p-1 bg-indigo-300", "Goodbye!")

}

trait MoreReusableStuff[F[_]] {

  case class State(counter: Int)

  // pure means state but no actions
  val dsl = ff4s.Dsl.pure[F, State]

  import dsl._
  import dsl.syntax.html._

  val counter = useState { state =>
    div(cls := "p-1 bg-teal-400", s"counter=${state.counter}")
  }

}

trait EvenMoreReusableStuff[F[_]] {

  case class State(disabled: Boolean)

  sealed trait Action

  object Action {
    case class ButtonClick() extends Action
  }

  val dsl = ff4s.Dsl[F, State, Action]

  import dsl._
  import dsl.syntax.html._

  val fancyButton = useState { state =>
    button(
      cls := s"p-1 text-center shadow rounded bg-pink-400 hover:bg-pink-300 active:bg-pink-500",
      disabled := state.disabled,
      onClick := (_ => Some(Action.ButtonClick())),
      "click me"
    )
  }

}

class App[F[_]: Async] {

  val F = Async[F]

  case class State(
      counter: Int = 0,
      buttonOff: Boolean = false
  )

  sealed trait Action
  object Action {
    case class ButtonClick() extends Action
  }

  implicit val store: Resource[F, Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { ref => (a: Action) =>
      a match {
        case Action.ButtonClick() =>
          ref.update(state =>
            state.copy(
              counter = state.counter + 1,
              buttonOff = if (state.counter > 5) true else false
            )
          )
      }
    }

  val dsl = ff4s.Dsl[F, State, Action]

  import dsl._
  import dsl.syntax.html._

  object rs extends ReusableStuff[F]
  object rs2 extends MoreReusableStuff[F]
  object rs3 extends EvenMoreReusableStuff[F]

  val app = div(
    cls := "mb-16 flex flex-col items-center",
    div(cls := "m-1", dsl.embed(rs.dsl)(rs.header)),
    div(
      cls := "m-1",
      dsl.embed(rs2.dsl, state => rs2.State(state.counter))(rs2.counter)
    ),
    div(
      cls := "m-1",
      dsl.embed(
        rs3.dsl,
        state => rs3.State(state.buttonOff),
        (action: rs3.Action) =>
          action match {
            case rs3.Action.ButtonClick() => Action.ButtonClick()
          }
      )(rs3.fancyButton)
    ),
    div(
      cls := "m-1",
      dsl.embed(rs.dsl)(rs.footer)
    )
  )

  def run: F[Nothing] = app.renderInto("#app")

}
