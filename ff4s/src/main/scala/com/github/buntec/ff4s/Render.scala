package com.github.buntec.ff4s

import cats.syntax.all._

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher

import org.scalajs.dom.document

import com.github.buntec.ff4s.snabbdom.patch

private[ff4s] object Render {

  def apply[F[_]: Async, State, Action](
      dsl: Dsl[F, State, Action],
      store: Resource[F, Store[F, State, Action]]
  )(
      view: dsl.View[VNode[F]],
      selector: String
  ): F[Nothing] = (for {
    dispatcher <- Dispatcher[F]
    root <- Resource.eval(Async[F].delay(document.querySelector(selector)))
    s <- store
    state0 <- Resource.eval(s.state.get)
    vnode0 <- Resource.eval(view.foldMap(Compiler(dsl, state0, s.dispatcher)))
    proxy0 <- Resource.eval(
      Async[F].delay(patch(root, vnode0.toSnabbdom(dispatcher)))
    )
    vnodes = s.state.discrete.evalMap(state =>
      Async[F]
        .timed(
          view.foldMap(Compiler(dsl, state, s.dispatcher))
        )
        .flatMap { case (elapsed, node) =>
          Async[F].delay {
            Logging.debug(s"compiling view took ${elapsed.toMillis} ms")
            node
          }
        }
    )
    _ <- vnodes
      .evalMapAccumulate(proxy0) { case (prevProxy, nextProxy) =>
        Async[F]
          .timed(
            Async[F].delay(patch(prevProxy, nextProxy.toSnabbdom(dispatcher)))
          )
          .flatMap { case (elapsed, vnode) =>
            Async[F].delay {
              Logging.debug(s"patching took ${elapsed.toMillis} ms")
              vnode
            }
          }
          .map(v => (v, ()))
      }
      .compile
      .resource
      .drain

  } yield ()).useForever

}
