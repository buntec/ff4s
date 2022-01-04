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
    compiler = Compiler(dsl, state0, s.dispatcher)
    vnode <- Resource.eval(view.foldMap(compiler))
    vnode0 <- Resource.eval(
      Async[F].delay(patch(root, vnode.toSnabbdom(dispatcher)))
    )
    vnodes = s.state.discrete.evalMap(state =>
      view.foldMap(Compiler(dsl, state, s.dispatcher))
    )
    _ <- vnodes
      .evalMapAccumulate(vnode0) { case (vnPrev, vnNext) =>
        Async[F]
          .delay(patch(vnPrev, vnNext.toSnabbdom(dispatcher)))
          .map(v => (v, ()))
      }
      .compile
      .resource
      .drain

  } yield ()).useForever

}
