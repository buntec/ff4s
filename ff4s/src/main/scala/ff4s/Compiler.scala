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
import cats.~>
import org.scalajs.dom

import scala.collection.mutable.HashMap

import scalajs.js

private[ff4s] trait Compiler[F[_], State, Action] {

  def apply(
      dsl: Dsl[State, Action],
      state: State,
      actionDispatch: Action => F[Unit]
  ): (dsl.ViewA ~> Id)

}

private[ff4s] object Compiler {

  def apply[F[_], State, Action](debug: Boolean) =
    new Compiler[F, State, Action] {

      private val literalsCache: HashMap[String, VNode[Action]] =
        collection.mutable.HashMap.empty[String, VNode[Action]]

      override def apply(
          dsl: Dsl[State, Action],
          state: State,
          actionDispatch: Action => F[Unit]
      ): (dsl.ViewA ~> Id) = {
        import dsl._

        if (debug) {
          js.Dynamic.global.ff4s_state = state.asInstanceOf[js.Any]
        }

        new (ViewA ~> Id) {

          private var id0: Long = 0L

          private def fromLiteral(html: String): VNode[Action] =
            VNode[Action] {
              val elm = dom.document.createElement("div")
              elm.innerHTML = html
              val vnode = snabbdom.toVNode(elm).toVNode
              vnode match {
                case snabbdom.VNode.Element(_, _, child :: Nil) =>
                  child // unwrap div if there is a single child
                case _ =>
                  vnode // otherwise keep the wrapper div (TODO: throw instead?)
              }
            }

          override def apply[A](fa: ViewA[A]): Id[A] = fa match {

            case GetState() => state

            case Text(s) => VNode[Action](snabbdom.VNode.text(s))

            case Empty() => VNode[Action](snabbdom.VNode.empty)

            case Literal(html, cache) =>
              if (cache) literalsCache.getOrElseUpdate(html, fromLiteral(html))
              else fromLiteral(html)

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
                  style
                ) =>
              VNode[F, Action](
                tag,
                children,
                cls,
                key,
                props,
                attrs,
                style,
                eventHandlers,
                onInsert,
                onDestroy
              )

            case GetUUID() => java.util.UUID.randomUUID()

            case GetId() => { id0 += 1; id0 }
          }

        }
      }
    }

}
