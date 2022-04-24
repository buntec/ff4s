package com.github.buntec.ff4s

import cats.~>

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher

import org.scalajs.dom

import com.github.buntec.snabbdom

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

  def apply[F[_]: Async, State, Action](
      dsl: Dsl[F, State, Action],
      state: State,
      dispatcher: Action => F[Unit]
  ): (dsl.ViewA ~> F) = {

    import dsl._

    new (ViewA ~> F) {

      override def apply[A](fa: ViewA[A]): F[A] = fa match {

        case GetState() => Async[F].pure(state)

        case Text(s) =>
          Async[F].pure {
            new VNode[F] {

              override def toSnabbdom(
                  dispatcher: Dispatcher[F]
              ): snabbdom.VNode = { s }

            }
          }

        case Empty() =>
          Async[F].pure {
            new VNode[F] {

              override def toSnabbdom(
                  dispatcher: Dispatcher[F]
              ): snabbdom.VNode = {
                null // Is this dangerous? An alternative would be `VNodeProxy.fromString("")`, but this results in an empty text element in the DOM
              }

            }
          }

        case Literal(html) =>
          Async[F].pure {
            new VNode[F] {

              override def toSnabbdom(
                  dispatcher: Dispatcher[F]
              ): snabbdom.VNode = {
                snabbdom.VNode.fromString(html) // TODO
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
              style,
              thunkArgs
            ) =>
          Async[F].delay {

            val renderFn = () => {

              var vn = children match {
                case Nil => VNode.empty[F](tag)
                case _   => VNode.parentNode[F](tag, children: _*)
              }

              if (props.nonEmpty) vn = vn.withProps(props)

              if (attrs.nonEmpty) vn = vn.withAttrs(attrs)

              if (style.nonEmpty) vn = vn.withStyle(style)

              key match {
                case Some(key0) => vn = vn.withKey(key0)
                case None       => ()
              }

              cls match {
                case Some(cls0) => vn = vn.withClass(cls0)
                case None       => ()
              }

              vn = eventHandlers.foldLeft(vn) {
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

              onInsert match {
                case Some(hook) =>
                  vn =
                    vn.withOnInsertHook((n: dom.Element) => dispatcher(hook(n)))
                case None => ()
              }

              onDestroy match {
                case Some(hook) =>
                  vn =
                    vn.withDestroyHook((n: dom.Element) => dispatcher(hook(n)))
                case None => ()
              }

              vn
            }

            thunkArgs match {
              case Some(args) =>
                new VNode[F] {
                  override def toSnabbdom(
                      dispatcher: Dispatcher[F]
                  ): snabbdom.VNode = snabbdom.thunk(
                    tag,
                    key.getOrElse(""): String,
                    (args: Seq[Any]) => renderFn().toSnabbdom(dispatcher),
                    Seq(args(state))
                  )
                }
              case _ =>
                new VNode[F] {
                  override private[ff4s] def toSnabbdom(
                      dispatcher: Dispatcher[F]
                  ): snabbdom.VNode = renderFn().toSnabbdom(dispatcher)
                }
            }

          }

      }

    }
  }

}
