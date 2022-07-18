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

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all._
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
      view: dsl.View[VNode[F]], // must be curried b/c of this
      selector: String
  ): F[Nothing] = {
    val F = Async[F]
    (for {
      dispatcher <- Dispatcher[F]
      root <- Resource.eval(F.delay(document.querySelector(selector)))
      s <- store
      state0 <- Resource.eval(s.state.get)
      vnode0 <- Resource.eval(
        F.pure(view.foldMap(Compiler(dsl, state0, s.dispatcher)))
      )
      proxy0 <- Resource.eval(
        F.delay(patch(root, vnode0.toSnabbdom(dispatcher)))
      )
      _ <- s.state.discrete
        .map(state => view.foldMap(Compiler(dsl, state, s.dispatcher)))
        .evalMapAccumulate(proxy0) { case (prevProxy, vnode) =>
          F.delay(patch(prevProxy, vnode.toSnabbdom(dispatcher))).map((_, ()))
        }
        .compile
        .resource
        .drain
    } yield ()).useForever
  }

}
