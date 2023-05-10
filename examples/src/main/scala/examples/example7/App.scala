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

package examples.example7

import cats.effect._
import cats.syntax.all._

// This is the example from the readme

final case class State(counter: Int = 0)

sealed trait Action
case class Inc(amount: Int) extends Action
case class Reset() extends Action

class App[F[_]](implicit F: Concurrent[F]) extends ff4s.App[F, State, Action] {

  override val store: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { _ =>
      _ match {
        case Inc(amount) =>
          state => state.copy(counter = state.counter + amount) -> none
        case Reset() => _.copy(counter = 0) -> none
      }
    }

  import dsl._ // provided by `ff4s.App`, see below
  import dsl.html._

  override val view = useState { state =>
    div(
      cls := "m-2 flex flex-col items-center", // tailwindcss classes
      h1("A counter"),
      div(s"value: ${state.counter}"),
      button(
        cls := "m-1 p-1 border",
        "increment",
        onClick := (_ => Some(Inc(1)))
      ),
      button(
        cls := "m-1 p-1 border",
        "decrement",
        onClick := (_ => Some(Inc(-1)))
      ),
      button(
        cls := "m-1 p-1 border",
        "reset",
        onClick := (_ => Some(Reset()))
      )
    )
  }

}
