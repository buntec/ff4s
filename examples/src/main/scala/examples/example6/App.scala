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
import cats.effect.kernel.Fiber
import cats.effect.std.MapRef
import cats.effect.std.Supervisor
import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration

import concurrent.duration._

// A minimal example showing how actions can be made cancellable.

final case class State(counter: Int = 0)

sealed trait Action

// Cancellable actions need a `cancelKey` to be used as a cancellation token.
sealed trait CancellableAction extends Action { def cancelKey: String }

// Increments the counter by `amount` after waiting for `delay`, unless cancelled.
case class Inc(delay: FiniteDuration, amount: Int, cancelKey: String)
    extends CancellableAction

// Cancels a running action with the given `cancelKey`; otherwise it is a no-op.
case class Cancel(cancelKey: String) extends Action

class App[F[_]](implicit F: Temporal[F]) extends ff4s.App[F, State, Action] {

  override val store = for {
    supervisor <- Supervisor[F]

    // we keep running actions (i.e. Fibers) in a map indexed by the cancellation key.
    fibers <- MapRef
      .ofSingleImmutableMap[F, String, Fiber[F, Throwable, Unit]]()
      .toResource

    store <- ff4s.Store[F, State, Action](State()) { state =>
      _ match {
        // repeated dispatch of `Inc` will cancel previous invocations if they haven't completed yet.
        case Inc(delay, amount, cancelKey) =>
          supervisor
            .supervise(
              F.sleep(delay) >> state.update(s =>
                s.copy(counter = s.counter + amount)
              )
            )
            .flatMap { fib =>
              fibers
                .getAndSetKeyValue(
                  cancelKey,
                  fib
                )
                .flatMap(_.foldMapM(_.cancel))
            }

        case Cancel(cancelKey) =>
          fibers(cancelKey).get.flatMap(_.foldMapM(_.cancel))
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
        onClick := (_ => Inc(1.second, 1, "inc").some)
      ),
      button(
        cls := btnCls,
        "-",
        onClick := (_ => Inc(1.second, -1, "inc").some)
      ),
      button(
        cls := btnCls,
        "cancel",
        onClick := (_ => Cancel("inc").some)
      )
    )
  }

}
