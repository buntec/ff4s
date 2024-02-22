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

import org.scalajs.dom

sealed trait VNode[Action] {

  private[ff4s] def toSnabbdom(actionDispatch: Action => Unit): snabbdom.VNode

  private[ff4s] final def modifyData(
      f: snabbdom.VNodeData => snabbdom.VNodeData
  ): VNode[Action] = new VNode[Action] {
    override private[ff4s] def toSnabbdom(
        actionDispatch: Action => Unit
    ): snabbdom.VNode = toSnabbdom(actionDispatch) match {
      case c @ snabbdom.VNode.Comment(_)       => c
      case e @ snabbdom.VNode.Element(_, _, _) => e.copy(data = f(e.data))
      case t @ snabbdom.VNode.Text(_)          => t
    }
  }

}

private[ff4s] object VNode {

  def apply[Action](snabbdomVNode: snabbdom.VNode): VNode[Action] =
    new VNode[Action] {
      override def toSnabbdom(actionDispatch: Action => Unit): snabbdom.VNode =
        snabbdomVNode
    }

  def apply[Action](
      tag: String,
      children: Seq[VNode[Action]],
      cls: Option[String],
      key: Option[String],
      props: Map[String, Any],
      attrs: Map[String, snabbdom.AttrValue],
      style: Map[String, String],
      handlers: Map[String, dom.Event => Option[Action]],
      onInsert: Option[dom.Element => Action],
      onDestroy: Option[dom.Element => Action]
  ): VNode[Action] = new VNode[Action] {
    override def toSnabbdom(actionDispatch: Action => Unit): snabbdom.VNode = {

      val insertHook = onInsert.map { hook =>
        new snabbdom.InsertHook {
          override def apply(vNode: snabbdom.PatchedVNode): Unit =
            actionDispatch(hook(vNode.node.asInstanceOf[dom.Element]))

        }
      }

      val destroyHook = onDestroy.map { hook =>
        new snabbdom.DestroyHook {
          override def apply(vNode: snabbdom.PatchedVNode): Unit =
            actionDispatch(hook(vNode.node.asInstanceOf[dom.Element]))

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
            handler(e).fold(())(action => actionDispatch(action))
          ))
        }
      )

      snabbdom.h(tag, data, children.map(_.toSnabbdom(actionDispatch)).toList)

    }
  }

  def apply[Action](text: String) = new VNode[Action] {
    override def toSnabbdom(actionDispatch: Action => Unit): snabbdom.VNode =
      text
  }

}
