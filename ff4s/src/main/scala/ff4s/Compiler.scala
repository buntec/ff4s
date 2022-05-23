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

import cats.Id
import cats.effect.std.Dispatcher
import cats.~>
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

  def apply[F[_], State, Action](
      dsl: Dsl[F, State, Action],
      state: State,
      actionDispatch: Action => F[Unit]
  ): (dsl.ViewA ~> Id) = {

    import dsl._

    new (ViewA ~> Id) {

      override def apply[A](fa: ViewA[A]): Id[A] = fa match {

        case GetState() => state

        case Text(s) =>
          new VNode[F] {
            override def toSnabbdom(
                dispatcher: Dispatcher[F]
            ): snabbdom.VNode = { s }
          }

        case Empty() =>
          new VNode[F] {
            override def toSnabbdom(
                dispatcher: Dispatcher[F]
            ): snabbdom.VNode = {
              snabbdom.VNode.create(
                None,
                snabbdom.VNodeData.empty,
                None,
                Some(""),
                None
              )
            }
          }

        case Literal(html) =>
          new VNode[F] {
            override def toSnabbdom(
                dispatcher: Dispatcher[F]
            ): snabbdom.VNode = {
              val elm = dom.document.createElement("div")
              elm.innerHTML = html
              snabbdom.toVNode(elm)
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
            ) => {

          thunkArgs match {
            case Some(args) => {

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
                  actionDispatch
                )
              }

              new VNode[F] {
                override def toSnabbdom(
                    dispatcher: Dispatcher[F]
                ): snabbdom.VNode = snabbdom.thunk(
                  tag,
                  key.getOrElse(""): String,
                  (_: Seq[Any]) =>
                    renderFn().toSnabbdom(
                      dispatcher
                    ), // TODO: this is broken
                  Seq(args(state))
                )
              }
            }
            case _ =>
              new VNode[F] {
                override private[ff4s] def toSnabbdom(
                    dispatcher: Dispatcher[F]
                ): snabbdom.VNode = {
                  VNode
                    .create[F, Action](
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
                      actionDispatch
                    )
                    .toSnabbdom(dispatcher)
                }
              }
          }

        }

      }

    }
  }

}
