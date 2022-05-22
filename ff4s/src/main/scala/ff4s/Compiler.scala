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

import cats.~>

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher

import org.scalajs.dom

private[ff4s] object Compiler {

  def transpile[F[_], StateA, StateB, ActionA, ActionB](
      dslA: Dsl[F, StateA, ActionA],
      dslB: Dsl[F, StateB, ActionB],
      f: StateB => StateA,
      g: ActionA => ActionB
  ): dslA.ViewA ~> dslB.View = {

    new (dslA.ViewA ~> dslB.View) {

      override def apply[A](fa: dslA.ViewA[A]): dslB.View[A] = fa match {
        case dslA.Text(s)       => dslB.text(s)
        case dslA.Literal(html) => dslB.literal(html)
        case dslA.Empty()       => dslB.empty
        case dslA.GetState()    => dslB.getState.map(f)
        case dslA.Element(
              tag,
              children,
              eventHandlers,
              cls,
              key,
              onInsert,
              onDestroy,
              props,
              attrs,
              style,
              thunkArgs
            ) =>
          dslB.element(
            tag,
            children,
            eventHandlers.map { case (key, handler) =>
              (key -> ((ev: dom.Event) => handler(ev).map(g)))
            },
            cls,
            key,
            onInsert.map(hook => (v: dom.Element) => g(hook(v))),
            onDestroy.map(hook => (v: dom.Element) => g(hook(v))),
            props,
            attrs,
            style,
            thunkArgs.map(t => (s: StateB) => t(f(s)))
          )
      }

    }
  }

  def apply[F[_]: Async, State, Action](
      dsl: Dsl[F, State, Action],
      state: State,
      dispatcher: Action => F[Unit]
  ): (dsl.ViewA ~> F) = {

    import dsl._

    new (ViewA ~> F) {

      override def apply[A](fa: ViewA[A]): F[A] = fa match {

        case GetState() => Async[F].pure(state)

        case Text(s) =>
          Async[F].pure {
            new VNode[F] {

              override def toSnabbdom(
                  dispatcher: Dispatcher[F]
              ): snabbdom.VNode = { s }

            }
          }

        case Empty() =>
          Async[F].pure {
            new VNode[F] {

              override def toSnabbdom(
                  dispatcher: Dispatcher[F]
              ): snabbdom.VNode = {
                null // Is this dangerous? An alternative would be `VNodeProxy.fromString("")`, but this results in an empty text element in the DOM
              }

            }
          }

        case Literal(html) =>
          Async[F].pure {
            new VNode[F] {
              override def toSnabbdom(
                  dispatcher: Dispatcher[F]
              ): snabbdom.VNode = {
                val elm = dom.document.createElement("div")
                elm.innerHTML = html
                snabbdom.toVNode(elm)
              }
            }
          }

        case Element(
              tag,
              children,
              eventHandlers,
              cls,
              key,
              onInsert,
              onDestroy,
              props,
              attrs,
              style,
              thunkArgs
            ) =>
          Async[F].delay {

            val renderFn = () => {

              VNode.create[F, Action](
                tag,
                children,
                cls,
                key,
                props,
                attrs,
                style,
                eventHandlers,
                onInsert,
                onDestroy,
                dispatcher
              )

            }

            thunkArgs match {
              case Some(args) =>
                new VNode[F] {
                  override def toSnabbdom(
                      dispatcher: Dispatcher[F]
                  ): snabbdom.VNode = snabbdom.thunk(
                    tag,
                    key.getOrElse(""): String,
                    (_: Seq[Any]) => renderFn().toSnabbdom(dispatcher), // TODO
                    Seq(args(state))
                  )
                }
              case _ =>
                new VNode[F] {
                  override private[ff4s] def toSnabbdom(
                      dispatcher: Dispatcher[F]
                  ): snabbdom.VNode = renderFn().toSnabbdom(dispatcher)
                }
            }

          }

      }

    }
  }

}
