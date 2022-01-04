package com.github.buntec.ff4s

import cats.effect.kernel.Async
import cats.~>

import org.scalajs.dom

import scala.scalajs.js

import com.github.buntec.ff4s.snabbdom.VNodeProxy
import cats.effect.std.Dispatcher
import com.github.buntec.ff4s.snabbdom.Snabby

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
              style
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
            style
          )
      }

    }
  }

  def apply[F[_]: Async, State, Action](
      dsl: Dsl[F, State, Action],
      state: State,
      dispatcher: Action => F[Unit]
  ): dsl.ViewA ~> F = {

    import dsl._

    new (ViewA ~> F) {

      override def apply[A](fa: ViewA[A]): F[A] = fa match {

        case GetState() => Async[F].pure(state)

        case Text(s) =>
          Async[F].delay {
            new VNode[F] {

              override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
                VNodeProxy.fromString(s)
              }

            }
          }

        case Empty() =>
          Async[F].delay {
            new VNode[F] {

              override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
                null // Is this dangerous? An alternative would be `VNodeProxy.fromString("")`, but this results in a text element in the DOM
              }

            }
          }

        case Literal(html) =>
          Async[F].delay {
            new VNode[F] {

              override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
                Snabby.apply(js.Array(html))
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
              style
            ) =>
          Async[F].delay {

            val base = children match {
              case Nil => VNode.empty[F](tag)
              case _   => VNode.parentNode[F](tag, children: _*)
            }

            val applyClass =
              (vn: VNode[F]) =>
                cls.map(cls0 => vn.withClass(cls0)).getOrElse(vn)

            val applyKey =
              (vn: VNode[F]) => key.map(key0 => vn.withKey(key0)).getOrElse(vn)

            val applyEventHandlers =
              (vn: VNode[F]) =>
                eventHandlers.foldLeft(vn) {
                  case (vnode, (eventName, handler)) =>
                    vnode.withEventHandler(
                      eventName,
                      (ev: dom.Event) =>
                        handler(ev) match {
                          case Some(action) => dispatcher(action)
                          case None         => Async[F].unit
                        }
                    )
                }

            val applyOnInsert =
              (vn: VNode[F]) =>
                onInsert
                  .map(hook =>
                    vn.withOnInsertHook((n: dom.Element) => dispatcher(hook(n)))
                  )
                  .getOrElse(vn)

            val applyOnDestroy =
              (vn: VNode[F]) =>
                onDestroy
                  .map(hook =>
                    vn.withDestroyHook((n: dom.Element) => dispatcher(hook(n)))
                  )
                  .getOrElse(vn)

            val applyProps = (vn: VNode[F]) =>
              if (props.nonEmpty) vn.withProps(props)
              else vn

            val applyAttrs = (vn: VNode[F]) =>
              if (attrs.nonEmpty) vn.withAttrs(attrs)
              else vn

            val applyStyle = (vn: VNode[F]) =>
              if (style.nonEmpty) vn.withStyle(style)
              else vn

            (applyProps andThen
              applyAttrs andThen
              applyStyle andThen
              applyKey andThen
              applyClass andThen
              applyEventHandlers andThen
              applyOnInsert andThen
              applyOnDestroy)(base)

          }

      }

    }
  }

}
