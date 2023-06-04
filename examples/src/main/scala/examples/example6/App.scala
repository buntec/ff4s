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

// A minimal example demonstrating the use of cancellation and running state.

final case class State(counter: Int = 0, loading: Boolean = false)

sealed trait Action

// Increments the counter after waiting for `delay`, unless cancelled.
case class DelayedInc(delay: FiniteDuration) extends Action

// Decrements the counter after waiting for `delay`, unless cancelled.
case class DelayedDec(delay: FiniteDuration) extends Action

case class Inc(amount: Int) extends Action

// Cancels any outstanding inc/dec.
case object Cancel extends Action

case class SetLoadingState(loading: Boolean) extends Action

class App[F[_]](implicit F: Temporal[F]) extends ff4s.App[F, State, Action] {

  private val incCancelKey = "inc"
  private val decCancelKey = "dec"
  private val loadingKey = "loading"

  override val store = ff4s
    .Store[F, State, Action](State()) { store =>
      _ match {
        case Inc(amount) =>
          state => state.copy(counter = state.counter + amount) -> none

        case DelayedInc(delay) =>
          _ -> store
            .withCancellationKey(incCancelKey)(
              store.withRunningState(loadingKey)(
                F.sleep(delay) *> store.dispatch(Inc(1))
              )
            )
            .some

        case DelayedDec(delay) =>
          _ -> store
            .withCancellationKey(decCancelKey)(
              store.withRunningState(loadingKey)(
                F.sleep(delay) *> store.dispatch(Inc(-1))
              )
            )
            .some

        case Cancel =>
          _ -> (
            store.cancel(incCancelKey),
            store.cancel(decCancelKey)
          ).parTupled.void.some

        case SetLoadingState(loading) =>
          _.copy(loading = loading) -> none
      }
    }
    .flatTap { store =>
      store
        .runningState(loadingKey)
        .discrete
        .evalMap(loading => store.dispatch(SetLoadingState(loading)))
        .compile
        .drain
        .background
    }

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
      ),
      if (state.loading) div("loading...") else empty
    )
  }

}
