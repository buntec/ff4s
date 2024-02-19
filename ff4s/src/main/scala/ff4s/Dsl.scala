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
import cats.free.Free
import cats.free.Free.liftF
import cats.syntax.all._
import ff4s.codecs._
import org.scalajs.dom

class Dsl[F[_], State, Action] { self =>

  private[ff4s] sealed trait ViewA[A]

  private[ff4s] case class Element(
      tag: String,
      children: Seq[VNode[F]],
      eventHandlers: Map[String, dom.Event => Option[Action]],
      cls: Option[String],
      key: Option[String],
      onInsert: Option[dom.Element => Action],
      onDestroy: Option[dom.Element => Action],
      props: Map[String, Any],
      attrs: Map[String, snabbdom.AttrValue],
      style: Map[String, String],
      thunkArgs: Option[State => Any]
  ) extends ViewA[VNode[F]]

  private[ff4s] case class Literal(html: String, cache: Boolean)
      extends ViewA[VNode[F]]

  private[ff4s] case class Text(s: String) extends ViewA[VNode[F]]

  private[ff4s] case class Empty() extends ViewA[VNode[F]]

  private[ff4s] case class GetState() extends ViewA[State]

  private[ff4s] case class GetUUID() extends ViewA[java.util.UUID]

  private[ff4s] case class GetId() extends ViewA[Long]

  type View[A] = Free[ViewA, A]

  /* The type of an ff4s program. */
  type V = View[VNode[F]]

  implicit class ViewOps[A](view: View[A]) {

    def translate[StateB, ActionB](
        dslB: Dsl[F, StateB, ActionB]
    )(implicit f: StateB => State, g: Action => ActionB): dslB.View[A] =
      view.foldMap(
        Compiler.transpile[F, State, StateB, Action, ActionB](self, dslB, f, g)
      )

  }

  def embed[A, StateB, ActionB](
      dslB: Dsl[F, StateB, ActionB],
      f: State => StateB,
      g: ActionB => Action
  )(view: dslB.View[A]): View[A] = view.foldMap(
    Compiler.transpile[F, StateB, State, ActionB, Action](dslB, self, f, g)
  )

  implicit class VOps(view: V) {

    /** Runs this ff4s program and renders it into the unique DOM node with the
      * given id. Prefer to use [[ff4s.IOEntryPoint]].
      */
    def renderInto(
        rootElementId: String
    )(implicit
        async: Async[F],
        store: Resource[F, Store[F, State, Action]]
    ): F[Unit] = Render(self, store)(view, rootElementId, replaceRoot = false)

    /** Runs this ff4s program and renders it into the unique DOM node with the
      * given id. Prefer to use [[ff4s.IOEntryPoint]].
      */
    def renderReplace(
        rootElementId: String
    )(implicit
        async: Async[F],
        store: Resource[F, Store[F, State, Action]]
    ): F[Unit] = Render(self, store)(view, rootElementId, replaceRoot = true)

  }

  /** Produces the current state. */
  def getState: View[State] = liftF[ViewA, State](GetState())

  /** Alias for `getState.flatMap(f)`. */
  def useState[A](f: State => View[A]): View[A] = getState.flatMap(f)

  /** Produces random UUIDs. For uses cases see
    * https://react.dev/reference/react/useId
    */
  def getUUID: View[java.util.UUID] = liftF[ViewA, java.util.UUID](GetUUID())

  /** Alias for `getUUID.flatMap(f)`. */
  def useUUID[A](f: java.util.UUID => View[A]): View[A] = getUUID.flatMap(f)

  /** Produces an increasing sequence of Ids: 1, 2, 3,... The advantage over
    * `getUUID` is that the IDs are stable across renders provided the order of
    * `getId` calls is stable. If global uniqueness is not required, this is
    * better for performance.
    */
  def getId: View[Long] = liftF[ViewA, Long](GetId())

  /** Alias for `getId.flatMap(f)`. */
  def useId[A](f: Long => View[A]): View[A] = getId.flatMap(f)

  private[ff4s] def element(
      tag: String,
      children: Seq[VNode[F]] = Seq.empty,
      eventHandlers: Map[String, dom.Event => Option[Action]] = Map.empty,
      cls: Option[String] = None,
      key: Option[String] = None,
      onInsert: Option[dom.Element => Action] = None,
      onDestroy: Option[dom.Element => Action] = None,
      props: Map[String, Any] = Map.empty,
      attrs: Map[String, snabbdom.AttrValue] = Map.empty,
      style: Map[String, String] = Map.empty,
      thunkArgs: Option[State => Any] = None
  ): V = liftF[ViewA, VNode[F]](
    Element(
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
    )
  )

  /** Constructs a ff4s program from a HTML or SVG string. This is useful for
    * things like SVG icons. Note that this methods is unsafe in the sense that
    * there is no compile-time validation of the provided string. By default,
    * the generated virtual DOM nodes are cached for performance. If you need to
    * create a very large or even unbounded number of literals, then consider
    * setting `cache = false` to avoid memory leaks.
    */
  def literal(html: String, cache: Boolean = true): V =
    liftF[ViewA, VNode[F]](Literal(html, cache))

  def text(s: String): V = liftF[ViewA, VNode[F]](Text(s))

  def empty: V = liftF[ViewA, VNode[F]](Empty())

  private case class ElemArgs(
      key: Option[String] = None,
      children: Seq[V] = Seq.empty,
      attrs: Map[String, snabbdom.AttrValue] = Map.empty,
      props: Map[String, Any] = Map.empty,
      style: Map[String, String] = Map.empty,
      eventHandlers: Map[String, dom.Event => Option[Action]] = Map.empty,
      insertHook: Option[dom.Element => Action] = None,
      destroyHook: Option[dom.Element => Action] = None,
      thunkArgs: Option[State => Any] = None
  )

  sealed trait Modifier

  object Modifier {

    case object NoOp extends Modifier

    case class Key(key: String) extends Modifier

    case class EventHandler(
        eventName: String,
        handler: dom.Event => Option[Action]
    ) extends Modifier

    case class HtmlAttr[A](
        name: String,
        value: A,
        codec: Codec[A, String]
    ) extends Modifier

    case class SvgAttr[A](
        name: String,
        value: A,
        codec: Codec[A, String]
    ) extends Modifier

    case class Prop[A, B](
        name: String,
        value: A,
        codec: Codec[A, B]
    ) extends Modifier

    case class ChildNode(view: V) extends Modifier

    case class Slot(name: String, elem: V) extends Modifier

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

  object key {
    def :=(s: String): Modifier = Modifier.Key(s)
    def :=(n: Int): Modifier = Modifier.Key(n.toString)
    def :=(x: Double): Modifier = Modifier.Key(x.toString)
  }

  /** This modifier does nothing. Can be useful in conditionals, e.g.,
    * ```
    * option(if (someCondition) defaultSelected := true else noop)
    * ```
    */
  val noop: Modifier = Modifier.NoOp

  /** Thunks are currently broken :( */
  object thunked {
    def :=(args: State => Any): Modifier = Modifier.Thunk(args)
  }

  object insertHook {
    def :=(onInsert: dom.Element => Action): Modifier =
      Modifier.InsertHook(onInsert)
  }

  object destroyHook {
    def :=(onDestroy: dom.Element => Action): Modifier =
      Modifier.DestroyHook(onDestroy)
  }

  implicit class SlotOps(slot: Slot) {
    def :=(element: V): Modifier = Modifier.Slot(slot.name, element)
  }

  implicit class HtmlAttrsOps[A](attr: HtmlAttr[A]) {
    def :=(value: A): Modifier =
      Modifier.HtmlAttr(attr.name, value, attr.codec)
  }

  implicit class HtmlPropOps[A, B](prop: HtmlProp[A, B]) {
    def :=(value: A): Modifier =
      Modifier.Prop[A, B](prop.name, value, prop.codec)
  }

  implicit class EventPropOps[Ev](prop: EventProp[Ev]) {
    def :=(handler: Ev => Option[Action]): Modifier =
      Modifier.EventHandler(
        prop.name,
        (ev: dom.Event) => handler(ev.asInstanceOf[Ev])
      )
  }

  implicit class WebComponentOps(wc: WebComponent) {
    def apply(modifiers: Modifier*): V = {
      (new HtmlTag[dom.html.Element](wc.tagName, false))(modifiers: _*)
    }
  }

  implicit class HtmlTagOps(tag: HtmlTag[_]) {

    def apply(modifiers: Modifier*): V = {

      val args = modifiers.foldLeft(ElemArgs()) { case (args, mod) =>
        mod match {
          case Modifier.NoOp     => args
          case Modifier.Key(key) => args.copy(key = Some(key))
          case Modifier.HtmlAttr(name, value, codec) =>
            if (codec == BooleanAsAttrPresenceCodec) {
              // this codec doesn't play nicely with snabbdom
              // https://github.com/snabbdom/snabbdom#the-attributes-module
              args.copy(attrs =
                args.attrs + (name -> value.asInstanceOf[Boolean])
              )
            } else {
              args.copy(attrs = args.attrs + (name -> codec.encode(value)))
            }
          case Modifier.SvgAttr(name, value, codec) =>
            args.copy(attrs = args.attrs + (name -> codec.encode(value)))
          case Modifier.Prop(name, value, codec) =>
            args.copy(props = args.props + (name -> codec.encode(value)))
          case Modifier.EventHandler(eventName, handler) =>
            args.copy(eventHandlers =
              args.eventHandlers + (eventName -> handler)
            )
          case Modifier.ChildNode(vnode) =>
            args.copy(children = args.children :+ vnode)
          case Modifier.ChildNodes(vnodes) =>
            args.copy(children = args.children ++ vnodes)
          case Modifier.InsertHook(onInsert) =>
            args.copy(insertHook = Some(onInsert))
          case Modifier.DestroyHook(onDestroy) =>
            args.copy(destroyHook = Some(onDestroy))
          case Modifier.Style(name, value) =>
            args.copy(style = args.style + (name -> value))
          case Modifier.Thunk(tArgs) => args.copy(thunkArgs = Some(tArgs))
          case Modifier.Slot(name, elem) =>
            val elemWithSlotAttr = elem.map(node =>
              VNode.modifyData(
                node,
                data => data.copy(attrs = data.attrs + ("slot" -> name))
              )
            )
            args.copy(children = args.children :+ elemWithSlotAttr)
        }
      }

      require(
        !tag.void || args.children.isEmpty,
        s"A $tag element cannot have child nodes."
      )

      args.children.sequence.flatMap { children =>
        element(
          tag.name,
          key = args.key,
          children = children,
          eventHandlers = args.eventHandlers,
          attrs = args.attrs,
          props = args.props,
          style = args.style,
          onInsert = args.insertHook,
          onDestroy = args.destroyHook,
          thunkArgs = args.thunkArgs
        )
      }

    }

  }

  implicit class SvgTagOps(tag: SvgTag[_]) {

    def apply(modifiers: Modifier*): V = {

      val args = modifiers.foldLeft(ElemArgs()) { case (args, mod) =>
        mod match {
          case Modifier.NoOp     => args
          case Modifier.Key(key) => args.copy(key = Some(key))
          case Modifier.HtmlAttr(name, value, codec) =>
            if (codec == BooleanAsAttrPresenceCodec) {
              // this codec doesn't play nicely with snabbdom
              // https://github.com/snabbdom/snabbdom#the-attributes-module
              args.copy(attrs =
                args.attrs + (name -> value.asInstanceOf[Boolean])
              )
            } else {
              args.copy(attrs = args.attrs + (name -> codec.encode(value)))
            }
          case Modifier.SvgAttr(name, value, codec) =>
            args.copy(attrs = args.attrs + (name -> codec.encode(value)))
          case Modifier.Prop(name, value, codec) =>
            args.copy(props = args.props + (name -> codec.encode(value)))
          case Modifier.EventHandler(eventName, handler) =>
            args.copy(eventHandlers =
              args.eventHandlers + (eventName -> handler)
            )
          case Modifier.ChildNode(vnode) =>
            args.copy(children = args.children :+ vnode)
          case Modifier.ChildNodes(vnodes) =>
            args.copy(children = args.children ++ vnodes)
          case Modifier.InsertHook(onInsert) =>
            args.copy(insertHook = Some(onInsert))
          case Modifier.DestroyHook(onDestroy) =>
            args.copy(destroyHook = Some(onDestroy))
          case Modifier.Style(name, value) =>
            args.copy(style = args.style + (name -> value))
          case Modifier.Thunk(tArgs) => args.copy(thunkArgs = Some(tArgs))
          case Modifier.Slot(name, elem) =>
            val elemWithSlotAttr = elem.map(node =>
              VNode.modifyData(
                node,
                data => data.copy(attrs = data.attrs + ("slot" -> name))
              )
            )
            args.copy(children = args.children :+ elemWithSlotAttr)
        }
      }

      args.children.sequence.flatMap { children =>
        element(
          tag.name,
          key = args.key,
          children = children,
          eventHandlers = args.eventHandlers,
          attrs = args.attrs,
          props = args.props,
          style = args.style,
          onInsert = args.insertHook,
          onDestroy = args.destroyHook,
          thunkArgs = args.thunkArgs
        )
      }

    }

  }

  implicit class SvgAttrsOps[A](attr: SvgAttr[A]) {
    def :=(value: A): Modifier =
      Modifier.SvgAttr(attr.name, value, attr.codec)
  }

  object html
      extends HtmlTags
      with HtmlAttrs
      with HtmlProps
      with GlobalEventProps {

    // Alternative: new HtmlAttr("class", StringAsIsCodec)
    lazy val cls = new HtmlProp[String, String]("className", StringAsIsCodec)
    lazy val `class` = cls
    lazy val className = cls

    lazy val styleAttr = new HtmlAttr("style", StringAsIsCodec)

    lazy val role = new HtmlAttr("role", StringAsIsCodec)

    lazy val rel = new HtmlAttr("rel", StringAsIsCodec)

    def aria(suffix: String) = new HtmlAttr("aria-" + suffix, StringAsIsCodec)

    def dataAttr(suffix: String) =
      new HtmlAttr("data-" + suffix, StringAsIsCodec)

  }

  object svg extends SvgTags with SvgAttrs {

    lazy val cls = new SvgAttr("class", StringAsIsCodec, None)
    lazy val `class` = cls
    lazy val className = cls

  }

}

object Dsl {

  def apply[F[_], State, Action]: Dsl[F, State, Action] =
    new Dsl[F, State, Action]

}
