package examples.example6

import cats.effect.Temporal
import cats.effect.implicits._
import concurrent.duration._
import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration
import cats.effect.std.MapRef
import cats.effect.kernel.Fiber
import cats.effect.std.Supervisor

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

    store <- ff4s.Store[F, State, Action](State()) { stateRef =>
      _ match {
        // repeated dispatch of `Inc` will cancel previous invocations if they haven't completed yet.
        case Inc(delay, amount, cancelKey) =>
          supervisor
            .supervise(
              F.sleep(delay) >> stateRef.update(s =>
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

  override val root = useState { state =>
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
