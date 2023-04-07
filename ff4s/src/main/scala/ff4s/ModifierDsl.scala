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

import com.raquo.domtypes.generic.codecs.Codec
import cats.free.Free

trait ModifierDsl[F[_], State, Action] { self: Dsl[F, State, Action] =>

  sealed trait Modifier

  object Modifier {

    case object NoOp extends Modifier

    case class Key(key: String) extends Modifier

    case class EventHandler(
        eventName: String,
        handler: dom.Event => Option[Action]
    ) extends Modifier

    case class HtmlAttr[V](
        name: String,
        value: V,
        codec: Codec[V, String]
    ) extends Modifier

    case class SvgAttr[V](
        name: String,
        value: V,
        codec: Codec[V, String]
    ) extends Modifier

    case class Prop[V, DomV](
        name: String,
        value: V,
        codec: Codec[V, DomV]
    ) extends Modifier

    case class ChildNode(view: V) extends Modifier

    implicit def fromView(view: V): Modifier = ChildNode(view)

    implicit def fromVNode(vnode: VNode[F]): Modifier = ChildNode(
      Free.pure[ViewA, VNode[F]](vnode)
    )

    implicit def fromString(
        text: String
    ): Modifier =
      fromVNode(VNode.fromString(text))

    case class ChildNodes(vnodes: Seq[V]) extends Modifier

    implicit def fromViews(views: Seq[V]): Modifier =
      ChildNodes(views)

    implicit def fromVNodes(
        vnodes: Seq[VNode[F]]
    ): Modifier =
      ChildNodes(vnodes.map(v => Free.pure[ViewA, VNode[F]](v)))

    case class InsertHook(onInsert: dom.Element => Action) extends Modifier

    case class DestroyHook(
        onDestroy: dom.Element => Action
    ) extends Modifier

    case class Style(name: String, value: String) extends Modifier

    case class Thunk(args: State => Any) extends Modifier

  }

}
