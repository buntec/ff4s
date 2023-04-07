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

import cats.effect.Temporal
import fs2.Stream
import cats.effect.implicits._
import concurrent.duration._
import cats.syntax.all._

case class State(counter: Int = 0)

sealed trait Action
case class Inc(amount: Int) extends Action

class App[F[_]](implicit F: Temporal[F]) extends ff4s.App[F, State, Action] {

  val store = ff4s
    .Store[F, State, Action](State()) { ref =>
      _ match {
        case Inc(amount) =>
          ref.update(state => state.copy(counter = state.counter + amount))
      }
    }
    .flatTap { store =>
      // increment the counter once per second
      Stream
        .fixedDelay[F](1.second)
        .evalMap(_ => store.dispatch(Inc(1)))
        .compile
        .drain
        .background
    }

  import dsl._
  import dsl.syntax.html._

  val root = useState { state =>
    div(
      cls := "m-2 flex flex-col items-center",
      h1("A counter"),
      span(s"value: ${state.counter}"),
      button(
        cls := "m-1 p-1 border",
        "increment",
        onClick := (_ => Some(Inc(1)))
      ),
      button(
        cls := "m-1 p-1 border",
        "decrement",
        onClick := (_ => Some(Inc(-1)))
      )
    )
  }

}

// object App extends ff4s.IOEntryPoint(new App)
