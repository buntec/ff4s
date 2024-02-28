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

import cats.Applicative
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

/** The store holds the entire state of your application. The only way to change
  * the state is to dispatch actions on it. Actions can also trigger
  * side-effects (think data fetching, etc.). Since the state is exposed as a
  * `fs2.concurrent.Signal[State]`, we can subscribe and react to changes of (a
  * subset of) the state. There should be only one instance of the store.
  *
  * The terminology is partly borrowed from
  * [[https://redux.js.org/api/store Redux]]. Note, however, that the "reducer"
  * in ff4s is more akin to an "update" function in
  * [[https://guide.elm-lang.org/effects/http Elm]].
  */
sealed trait Store[F[_], State, Action] {

  /** Adds `action` to the queue of actions to be evaluated. */
  def dispatch(action: Action): F[Unit]

  /** Holds the current application state. */
  def state: Signal[F, State]

  /** Wraps `fu` and makes it cancelable using `cancel(key)`. Repeated
    * evaluation of the returned effect will cancel previous evaluations. The
    * returned effect runs exactly as long as `fu`. In particular,
    * `withRunningState(...)(withCancellationKey(...)(fu))` has the same
    * semantics as `withCancellationKey(...)(withRunningState(...)(fu))`.
    *
    * WARNING: Nested wrapping using the same key more than once results in
    * undefined behavior of the returned effect (`fu` may or may not run, and a
    * fiber might deadlock).
    */
  def withCancellationKey(key: String)(fu: F[Unit]): F[Unit]

  /** See `withCancellationKey`. */
  def cancel(key: String): F[Unit]

  /** Ensures that `runningState(key)` evaluates to `true` while `fu` is
    * running. Useful for things like loading indicators.
    */
  def withRunningState(key: String)(fu: F[Unit]): F[Unit]

  /** See `withRunningState`. */
  def runningState(key: String): Signal[F, Boolean]

}

object Store {

  /** A simple type of store where all actions are pure state updates.
    */
  def pure[F[_]: Concurrent, State, Action](init: State)(
      update: (Action, State) => State
  ): Resource[F, Store[F, State, Action]] = {
    val unit = Concurrent[F].unit
    apply[F, State, Action](init)(_ => (a, s) => update(a, s) -> unit)
  }

  /** Constructs a store by assigning every action to a state update and/or
    * side-effect. The side-effect will be run *after* the state update. The
    * store itself is injected into the constructor so that we may dispatch
    * further actions as side-effects:
    * ```
    *   case (FooAction(foo), state) => state.copy(foo = foo) -> store.dispatch(BarAction())
    *
    * ```
    *
    * @param init
    *   the initial state of the app
    * @param mkUpdate
    *   given the store instance, this should return an update function mapping
    *   actions to state updates and/or side-effects.
    *
    * @return
    *   the store
    */
  def apply[F[_]: Concurrent, State, Action](init: State)(
      mkUpdate: Store[F, State, Action] => (Action, State) => (State, F[Unit])
  ): Resource[F, Store[F, State, Action]] = for {
    supervisor <- Supervisor[F]

    actionQ <- Queue.unbounded[F, Action].toResource

    stateSR <- SignallingRef.of[F, State](init).toResource

    fiberMR <- MapRef
      .ofSingleImmutableMap[F, String, Fiber[F, Throwable, Unit]]()
      .toResource

    runningCount <- SignallingMapRef
      .ofSingleImmutableMap[F, String, Int]()
      .toResource

    withRunningStateR = (key: String) =>
      Resource.make(runningCount(key).update(_.fold(1)(_ + 1).some))(_ =>
        runningCount(key).update(_.map(_ - 1))
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

    unit = Applicative[F].unit

    _ <- Stream
      .fromQueueUnterminated(actionQ)
      .evalMap(action =>
        stateSR
          .modify(state => update(action, state))
          .flatMap { fu =>
            if (fu == unit) unit
            else supervisor.supervise(fu).void
          }
      )
      .compile
      .drain
      .background

  } yield store

}
