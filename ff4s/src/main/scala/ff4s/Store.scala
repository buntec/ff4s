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

package ff4s

import cats.effect.Concurrent
import cats.effect.Fiber
import cats.effect.Resource
import cats.effect.implicits._
import cats.effect.std.MapRef
import cats.effect.std.Queue
import cats.effect.std.Supervisor
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.SignallingMapRef
import fs2.concurrent.SignallingRef

trait Store[F[_], State, Action] {

  /** Adds `action` to the queue of actions to be evaluated. */
  def dispatch(action: Action): F[Unit]

  /** Holds the current application state. */
  def state: Signal[F, State]

  /** Wraps an effect to make it cancelable using `cancel(key)`. (For this to
    * work the provided effect must be cancelable.) Repeated evaluation of the
    * resulting effect will cancel previous evaluations. WARNING: wrapping the
    * same effect more than once with the same key results in undefined behavior
    * (the wrapped effect may or may not run, and a fiber might leak).
    */
  def withCancellationKey(key: String)(fu: F[Unit]): F[Unit]

  /** See `withCancellationKey`. */
  def cancel(key: String): F[Unit]

  /** Ensures that `runningState(key)` evaluates to `true` while the wrapped
    * effect is running. Useful for things like loading indicators.
    */
  def withRunningState(key: String)(fu: F[Unit]): F[Unit]

  /** See `withRunningState.` */
  def runningState(key: String): Signal[F, Boolean]

}

object Store {

  def apply[F[_]: Concurrent, State, Action](init: State)(
      mkUpdate: Store[F, State, Action] => Action => State => (
          State,
          Option[F[Unit]]
      )
  ): Resource[F, Store[F, State, Action]] = for {
    supervisor <- Supervisor[F]

    actionQ <- Queue.unbounded[F, Action].toResource

    stateSR <- SignallingRef.of[F, State](init).toResource

    fiberMR <- MapRef
      .ofSingleImmutableMap(
        Map.empty[String, Fiber[F, Throwable, Unit]]
      )
      .toResource

    runningCount <- SignallingMapRef
      .ofSingleImmutableMap[F, String, Int]()
      .toResource

    withRunningStateR = (key: String) =>
      Resource.make(runningCount(key).update {
        case None    => Some(1)
        case Some(n) => Some(n + 1)
      })(_ =>
        runningCount(key).update {
          case None    => None
          case Some(n) => Some(n - 1)
        }
      )

    store = new Store[F, State, Action] {

      override def dispatch(action: Action): F[Unit] = actionQ.offer(action)

      override def state: Signal[F, State] = stateSR

      override def withCancellationKey(
          key: String
      )(fu: F[Unit]): F[Unit] =
        supervisor
          .supervise(fu)
          .flatMap(fiber =>
            fiberMR(key)
              .getAndSet(fiber.some)
              .flatMap(_.foldMapM(_.cancel)) *> fiber.join
              .onCancel(fiber.cancel)
              .void
          )

      override def cancel(key: String): F[Unit] =
        fiberMR(key).getAndSet(none).flatMap(_.foldMapM(_.cancel))

      override def withRunningState(key: String)(fu: F[Unit]): F[Unit] =
        withRunningStateR(key).surround(fu)

      override def runningState(key: String): Signal[F, Boolean] =
        runningCount(key).map(_.exists(_ > 0)).changes

    }

    update = mkUpdate(store)

    _ <- Stream
      .fromQueueUnterminated(actionQ)
      .evalMap(action =>
        stateSR
          .modify(update(action))
          .flatMap(_.foldMapM(supervisor.supervise(_).void))
      )
      .compile
      .drain
      .background

  } yield store

}
