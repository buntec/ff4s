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

import cats.effect.Temporal
import cats.effect.implicits._
import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration

import concurrent.duration._

// A minimal example showing how long-running effects can be made cancellable.

final case class State(counter: Int = 0)

sealed trait Action

// Increments the counter after waiting for `delay`, unless cancelled.
case class DelayedInc(delay: FiniteDuration) extends Action

// Decrements the counter after waiting for `delay`, unless cancelled.
case class DelayedDec(delay: FiniteDuration) extends Action

case class Inc(amount: Int) extends Action

// Cancels any outstanding inc/dec.
case object Cancel extends Action

class App[F[_]](implicit F: Temporal[F]) extends ff4s.App[F, State, Action] {

  val makeCancelKey = ff4s.CancellationKey[F].toResource

  override val store = for {
    (incCancelKey, decCancelKey) <- (
      makeCancelKey,
      makeCancelKey
    ).parTupled

    store <- ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case Inc(amount) =>
          state => state.copy(counter = state.counter + amount) -> none

        case DelayedInc(delay) =>
          _ -> store
            .withCancellationKey(incCancelKey)(
              F.sleep(delay) *> store.dispatch(Inc(1))
            )
            .some

        case DelayedDec(delay) =>
          _ -> store
            .withCancellationKey(decCancelKey)(
              F.sleep(delay) *> store.dispatch(Inc(-1))
            )
            .some

        case Cancel =>
          _ -> (
            store.cancel(incCancelKey),
            store.cancel(decCancelKey)
          ).parTupled.void.some
      }
    }
  } yield store

  import dsl._
  import dsl.html._

  override val view = useState { state =>
    val btnCls = "m-1 p-2 border rounded"
    div(
      span(s"count: ${state.counter}"),
      button(
        cls := btnCls,
        "+",
        onClick := (_ => DelayedInc(1.second).some)
      ),
      button(
        cls := btnCls,
        "-",
        onClick := (_ => DelayedDec(1.second).some)
      ),
      button(
        cls := btnCls,
        "cancel",
        onClick := (_ => Cancel.some)
      )
    )
  }

}
