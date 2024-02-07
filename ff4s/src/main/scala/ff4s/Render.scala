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
import cats.effect.std.Env
import cats.syntax.all._
import org.scalajs.dom
import org.scalajs.dom.document
import snabbdom.PatchedVNode

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

  def apply[F[_], State, Action](
      dsl: Dsl[F, State, Action],
      store: Resource[F, Store[F, State, Action]]
  )(
      view: dsl.V, // must be curried b/c of dependent type
      rootElementId: String,
      replaceRoot: Boolean
  )(implicit F: Async[F]): F[Unit] =
    (store, Dispatcher.sequential[F]).tupled.use { case (store, dispatcher) =>
      val env = Env.make[F]
      for {
        debug <- env
          .get("FF4S_DEBUG")
          .map(_.flatMap(_.toBooleanOption).getOrElse(false))
        compiler = Compiler[F, State, Action](debug)
        root <- F.delay {
          val root0 = document.getElementById(rootElementId)
          if (replaceRoot) root0
          else
            root0
              .appendChild(document.createElement("div"))
              .asInstanceOf[dom.Element]
        }
        state0 <- store.state.get
        vnode0 <- F.delay(view.foldMap(compiler(dsl, state0, store.dispatch)))
        proxy0 <- F.delay(patch(root, vnode0.toSnabbdom(dispatcher)))
        _ <- store.state.discrete
          .map(state => view.foldMap(compiler(dsl, state, store.dispatch)))
          .evalMapAccumulate(proxy0) { case (prevProxy, vnode) =>
            F.async_[PatchedVNode] { cb =>
              dom.window.requestAnimationFrame { _ =>
                val proxy = patch(prevProxy, vnode.toSnabbdom(dispatcher))
                cb(Right(proxy))
              }
              ()
            }.tupleRight(())
          }
          .compile
          .drain
      } yield ()
    }

}
