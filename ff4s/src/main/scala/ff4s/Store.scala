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

import cats.effect.implicits._
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.std.Supervisor
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef

trait Store[F[_], State, Action] {

  def dispatch(action: Action): F[Unit]

  def state: Signal[F, State]

}

object Store {

  def apply[F[_]: Concurrent, State, Action](init: State)(
      update: Action => State => (State, F[Option[Action]])
  ): Resource[F, Store[F, State, Action]] = for {
    supervisor <- Supervisor[F]

    actionQ <- Queue.unbounded[F, Action].toResource

    stateSR <- SignallingRef.of[F, State](init).toResource

    _ <- Stream
      .fromQueueUnterminated(actionQ)
      .evalMap(action =>
        stateSR
          .modify(update(action))
          .flatMap(foa =>
            supervisor.supervise(foa.flatMap(_.foldMapM(actionQ.offer)))
          )
      )
      .compile
      .drain
      .background

  } yield (new Store[F, State, Action] {

    override def dispatch(action: Action): F[Unit] = actionQ.offer(action)

    override def state: Signal[F, State] = stateSR

  })

}
