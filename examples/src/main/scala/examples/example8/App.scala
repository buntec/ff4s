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

package examples.example8

import cats.effect._
import cats.syntax.all._
import io.circe.generic.semiauto._
import io.circe._

final case class State(fact: String = "")

case class Fact(text: String)
object Fact {
  implicit val codec: Codec[Fact] = deriveCodec
}

sealed trait Action
case class Generate() extends Action
case class SetFact(fact: String) extends Action

class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {

  override val store: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case SetFact(str) => _.copy(fact = str) -> none
        case Generate() =>
          (
            _,
            ff4s
              .HttpClient[F]
              .get[Fact](s"http://numbersapi.com/random?json")
              .flatMap { fact =>
                store.dispatch(SetFact(fact.text))
              }
              .some
          )

      }
    }

  import dsl._ // provided by `ff4s.App`, see below
  import dsl.html._

  override val view = useState { state =>
    div(
      cls := "m-2 flex flex-col items-center",
      h1("Http calls"),
      button(
        cls := "m-1 p-1 border",
        "generate",
        onClick := (_ => Generate().some)
      ),
      div(s"fact: ${state.fact}")
    )
  }

}
