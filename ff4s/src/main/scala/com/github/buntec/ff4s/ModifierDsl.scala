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

    case class ChildNode(view: View[VNode[F]]) extends Modifier

    implicit def fromView(view: View[VNode[F]]): Modifier = ChildNode(view)

    implicit def fromVNode(
        vnode: VNode[F]
    ): Modifier =
      ChildNode(Free.pure[ViewA, VNode[F]](vnode))

    implicit def fromString(
        text: String
    ): Modifier =
      fromVNode(VNode.fromString(text))

    case class ChildNodes(vnodes: Seq[View[VNode[F]]]) extends Modifier

    implicit def fromViews(views: Seq[View[VNode[F]]]): Modifier =
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
