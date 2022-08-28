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
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import cats.effect.kernel.Async
import org.http4s.Uri

trait Store[F[_], State, Action] {

  def dispatcher: Store.Dispatcher[F, Action]

  def state: Signal[F, State]

}

object Store {

  type Dispatcher[F[_], Action] = Action => F[Unit]

  def apply[F[_]: Concurrent, State, Action](
      initial: State
  )(
      builder: SignallingRef[F, State] => Dispatcher[F, Action]
  ): Resource[F, Store[F, State, Action]] = for {
    ref <- Resource.eval(SignallingRef.of[F, State](initial))
    disp = builder(ref)
  } yield (new Store[F, State, Action] {

    override def dispatcher: Dispatcher[F, Action] = disp

    override def state: Signal[F, State] = ref

  })

  def withRouter[F[_], State, Action](initial: State)(
      onUriChange: Uri => Action
  )(
      builder: (
          SignallingRef[F, State],
          Router[F]
      ) => Dispatcher[F, Action]
  )(implicit F: Async[F]) = for {

    ref <- Resource.eval(SignallingRef.of[F, State](initial))

    history <- History[F, Unit]

    router <- Router[F](history)

    disp = builder(ref, router)

    _ <- router.location.discrete
      .evalMap(uri => disp(onUriChange(uri)))
      .compile
      .drain
      .background

  } yield new Store[F, State, Action] {

    override def dispatcher: Dispatcher[F, Action] = disp

    override def state: Signal[F, State] = ref

  }

}
