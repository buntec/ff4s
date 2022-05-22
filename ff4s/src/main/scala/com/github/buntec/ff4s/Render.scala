package ff4s

import cats.syntax.all._

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher

import org.scalajs.dom.document

private[ff4s] object Render {

  val patch = snabbdom.init(
    Seq(
      snabbdom.modules.Attributes.module,
      snabbdom.modules.Classes.module,
      snabbdom.modules.Props.module,
      snabbdom.modules.Styles.module,
      snabbdom.modules.EventListeners.module,
      snabbdom.modules.Dataset.module
    )
  )

  def apply[F[_]: Async, State, Action](
      dsl: Dsl[F, State, Action],
      store: Resource[F, Store[F, State, Action]]
  )(
      view: dsl.View[VNode[F]],
      selector: String
  ): F[Nothing] = {
    val F = Async[F]
    (for {
      dispatcher <- Dispatcher[F]
      root <- Resource.eval(F.delay(document.querySelector(selector)))
      s <- store
      state0 <- Resource.eval(s.state.get)
      vnode0 <- Resource.eval(view.foldMap(Compiler(dsl, state0, s.dispatcher)))
      proxy0 <- Resource.eval(
        F.delay(patch(root, vnode0.toSnabbdom(dispatcher)))
      )
      _ <- s.state.discrete
        .evalMap(state =>
          F.timed(
            view.foldMap(Compiler(dsl, state, s.dispatcher))
          ).flatMap { case (elapsed, vnode) =>
            F.delay {
              Logging.debug(s"compiling view took ${elapsed.toMillis} ms")
              vnode
            }
          }
        )
        .evalMapAccumulate(proxy0) { case (prevProxy, vnode) =>
          F.timed(
            F.delay(patch(prevProxy, vnode.toSnabbdom(dispatcher)))
          ).flatMap { case (elapsed, nextProxy) =>
            F.delay {
              Logging.debug(s"patching DOM took ${elapsed.toMillis} ms")
              nextProxy
            }
          }.map((_, ()))
        }
        .compile
        .resource
        .drain

    } yield ()).useForever
  }

}
