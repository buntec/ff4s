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
import com.raquo.domtypes.generic.builders
import com.raquo.domtypes.generic.codecs.BooleanAsAttrPresenceCodec
import com.raquo.domtypes.jsdom.defs.tags._
import org.scalajs.dom

import annotation.nowarn

class Dsl[F[_], State, Action]
    extends EventPropsDsl[F, State, Action]
    with HtmlAttrsDsl[F, State, Action]
    with SvgAttrsDsl[F, State, Action]
    with PropDsl[F, State, Action]
    with StyleDsl[F, State, Action]
    with ModifierDsl[F, State, Action] { self =>

  sealed trait ViewA[A]

  case class Element(
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

  case class Literal(html: String) extends ViewA[VNode[F]]

  case class Text(s: String) extends ViewA[VNode[F]]

  case class Empty() extends ViewA[VNode[F]]

  case class GetState() extends ViewA[State]

  type View[A] = Free[ViewA, A]

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

  @nowarn("msg=dead code")
  def embed[A, StateB](
      dslB: Dsl[F, StateB, Nothing],
      f: State => StateB
  )(view: dslB.View[A]): View[A] = view.foldMap(
    Compiler.transpile[F, StateB, State, Nothing, Action](
      dslB,
      self,
      f,
      identity
    )
  )

  @nowarn("msg=dead code")
  def embed[A](dslB: Dsl[F, Unit, Nothing])(view: dslB.View[A]): View[A] =
    view.foldMap(
      Compiler.transpile[F, Unit, State, Nothing, Action](
        dslB,
        self,
        _ => (),
        identity
      )
    )

  implicit class ViewOfVNodeOps(view: View[VNode[F]]) {

    def renderInto(
        selector: String
    )(implicit async: Async[F], store: Resource[F, Store[F, State, Action]]) =
      Render.apply(self, store)(view, selector)

  }

  def getState: View[State] = liftF[ViewA, State](GetState())

  /** Alias for `getState.flatMap(f)`.
    */
  def useState[A](f: State => View[A]): View[A] = getState.flatMap(f)

  def element(
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
  ): View[VNode[F]] = liftF[ViewA, VNode[F]](
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

  def literal(html: String): View[VNode[F]] =
    liftF[ViewA, VNode[F]](Literal(html))

  def text(s: String): View[VNode[F]] = liftF[ViewA, VNode[F]](Text(s))

  def empty: View[VNode[F]] = liftF[ViewA, VNode[F]](Empty())

  private case class ElemArgs(
      key: Option[String] = None,
      children: Seq[View[VNode[F]]] = Seq.empty,
      attrs: Map[String, snabbdom.AttrValue] = Map.empty,
      props: Map[String, Any] = Map.empty,
      style: Map[String, String] = Map.empty,
      eventHandlers: Map[String, dom.Event => Option[Action]] = Map.empty,
      insertHook: Option[dom.Element => Action] = None,
      destroyHook: Option[dom.Element => Action] = None,
      thunkArgs: Option[State => Any] = None
  )

  class ElementBuilder[A](tag: String, void: Boolean) {

    def apply(
        modifiers: Modifier*
    ): View[VNode[F]] = {

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
        }
      }

      require(
        !void || args.children.isEmpty,
        s"A $tag element cannot have child nodes."
      )

      args.children.sequence.flatMap { children =>
        element(
          tag,
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

  trait TagBuilder
      extends builders.HtmlTagBuilder[ElementBuilder, dom.html.Element]
      with builders.SvgTagBuilder[ElementBuilder, dom.svg.Element] {

    @inline override protected def htmlTag[Ref <: dom.html.Element](
        tagName: String,
        void: Boolean
    ): ElementBuilder[Ref] = new ElementBuilder(tagName, void)

    @inline override protected def svgTag[Ref <: dom.svg.Element](
        tagName: String,
        void: Boolean
    ): ElementBuilder[Ref] = new ElementBuilder(tagName, void)

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
  val noop = Modifier.NoOp

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

  trait tagsSyntax
      extends GroupingTags[ElementBuilder]
      with TextTags[ElementBuilder]
      with EmbedTags[ElementBuilder]
      with FormTags[ElementBuilder]
      with SectionTags[ElementBuilder]
      with TableTags[ElementBuilder]
      with TagBuilder

  object syntax {

    object html
        extends tagsSyntax
        with StylesSyntax
        with EventPropsSyntax
        with PropsSyntax
        with HtmlAttrsSyntax

    object svg
        extends SvgTags[ElementBuilder]
        with TagBuilder
        with SvgAttrsSyntax

    object extras
        extends DocumentTags[ElementBuilder]
        with MiscTags[ElementBuilder]
        with TagBuilder

  }

}

object Dsl {

  /*
   * A fully general DSL for interactive components that depend on state.
   */
  def apply[F[_], State, Action] = new Dsl[F, State, Action]

  /*
   * A DSL for building components that may depend on state
   * but are not interactive (the `Action` type is `Nothing`).
   * Useful for writing non-interactive reusable components.
   */
  def pure[F[_], State] = new Dsl[F, State, Nothing]

  /*
   * A DSL for building components that don't depend on state
   * (the `State` type is `Unit`) and are not interactive
   * (the `Action` type is `Nothing`).
   * Useful for writing non-interactive, non-state-dependent
   * reusable components such as SVG icons.
   */
  def constant[F[_]] = new Dsl[F, Unit, Nothing]

}
