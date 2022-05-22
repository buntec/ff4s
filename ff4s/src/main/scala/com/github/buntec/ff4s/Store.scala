package ff4s

import cats.effect.kernel.Async
import cats.effect.kernel.Resource

import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef

trait Store[F[_], State, Action] {

  def dispatcher: Store.Dispatcher[F, Action]

  def state: Signal[F, State]

}

object Store {

  type Dispatcher[F[_], Action] = Action => F[Unit]

  def apply[F[_]: Async, State, Action](
      initial: State
  )(
      toDispatcher: SignallingRef[F, State] => Dispatcher[F, Action]
  ): Resource[F, Store[F, State, Action]] = for {
    ref <- Resource.eval(SignallingRef.of[F, State](initial))
    disp = toDispatcher(ref)
  } yield (new Store[F, State, Action] {

    override def dispatcher: Action => F[Unit] = disp

    override def state: Signal[F, State] = ref

  })

}
