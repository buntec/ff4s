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

import cats.effect.std.Dispatcher
import org.scalajs.dom

sealed trait VNode[F[_]] {

  private[ff4s] def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode

}

private[ff4s] object VNode {

  private[ff4s] def modifyData[F[_]](
      node: VNode[F],
      f: snabbdom.VNodeData => snabbdom.VNodeData
  ): VNode[F] = new VNode[F] {

    override private[ff4s] def toSnabbdom(
        dispatcher: Dispatcher[F]
    ): snabbdom.VNode = node.toSnabbdom(dispatcher) match {
      case c @ snabbdom.VNode.Comment(_)       => c
      case e @ snabbdom.VNode.Element(_, _, _) => e.copy(data = f(e.data))
      case t @ snabbdom.VNode.Text(_)          => t
    }

  }

  def apply[F[_]](snabbdomVNode: snabbdom.VNode): VNode[F] =
    new VNode[F] {
      override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode =
        snabbdomVNode
    }

  def apply[F[_]](mkSnabbdomVNode: Dispatcher[F] => snabbdom.VNode): VNode[F] =
    new VNode[F] {
      override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode =
        mkSnabbdomVNode(dispatcher)
    }

  def apply[F[_], Action](
      tag: String,
      children: Seq[VNode[F]],
      cls: Option[String],
      key: Option[String],
      props: Map[String, Any],
      attrs: Map[String, snabbdom.AttrValue],
      style: Map[String, String],
      handlers: Map[String, dom.Event => Option[Action]],
      onInsert: Option[dom.Element => Action],
      onDestroy: Option[dom.Element => Action],
      actionDispatch: Action => F[Unit]
  ): VNode[F] = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {

      val insertHook = onInsert.map { hook =>
        new snabbdom.InsertHook {
          override def apply(vNode: snabbdom.PatchedVNode): Unit =
            dispatcher.unsafeRunAndForget(
              actionDispatch(hook(vNode.node.asInstanceOf[dom.Element]))
            )
        }
      }

      val destroyHook = onDestroy.map { hook =>
        new snabbdom.DestroyHook {
          override def apply(vNode: snabbdom.PatchedVNode): Unit =
            dispatcher.unsafeRunAndForget(
              actionDispatch(hook(vNode.node.asInstanceOf[dom.Element]))
            )
        }
      }

      val data = snabbdom.VNodeData(
        attrs = cls.fold(attrs)(cls => attrs + ("class" -> cls)),
        props = props,
        style = style,
        key = key,
        hook = Some(snabbdom.Hooks(insert = insertHook, destroy = destroyHook)),
        on = handlers.map { case (eventName, handler) =>
          (eventName -> snabbdom.EventHandler((e: dom.Event) =>
            handler(e).fold(())(action =>
              dispatcher.unsafeRunAndForget(actionDispatch(action))
            )
          ))
        }
      )

      snabbdom.h(tag, data, children.map(_.toSnabbdom(dispatcher)).toList)

    }
  }

  def fromString[F[_]](text: String) = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode =
      text
  }

}
