package com.github.buntec.ff4s

import cats.syntax.all._

import cats.free.Free
import cats.free.Free.liftF

import cats.effect.kernel.Resource
import cats.effect.kernel.Async

import org.scalajs.dom

import com.raquo.domtypes.generic.builders
import com.raquo.domtypes.generic.codecs.BooleanAsAttrPresenceCodec
import com.raquo.domtypes.jsdom.defs.tags._

import com.github.buntec.ff4s.snabbdom.DataObject

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
      attrs: Map[String, DataObject.AttrValue],
      style: Map[String, String],
      thunkArgs: Option[State => Any]
  ) extends ViewA[VNode[F]]

  case class Literal(html: String) extends ViewA[VNode[F]]

  case class Text(s: String) extends ViewA[VNode[F]]

  case class Empty() extends ViewA[VNode[F]]

  case class GetState() extends ViewA[State]

  type View[A] = Free[ViewA, A]

  implicit class ViewOps[A](view: View[A]) {

    def lift[StateB, ActionB](
        dslB: Dsl[F, StateB, ActionB]
    )(implicit f: StateB => State, g: Action => ActionB): dslB.View[A] =
      view.foldMap(
        Compiler.transpile[F, State, StateB, Action, ActionB](self, dslB, f, g)
      )

  }

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
      attrs: Map[String, DataObject.AttrValue] = Map.empty,
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
      attrs: Map[String, DataObject.AttrValue] = Map.empty,
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

      // toList is needed in 2.12 apparently
      args.children.toList.sequence.flatMap { children =>
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
