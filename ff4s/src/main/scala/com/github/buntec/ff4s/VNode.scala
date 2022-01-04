package com.github.buntec.ff4s

import scala.scalajs.js
import js.JSConverters._

import cats.effect.std.Dispatcher

import org.scalajs.dom

import com.github.buntec.ff4s.snabbdom.DataObject
import com.github.buntec.ff4s.snabbdom.Hooks
import com.github.buntec.ff4s.snabbdom.Snabbdom
import com.github.buntec.ff4s.snabbdom.VNodeProxy

trait VNode[F[_]] {

  private[ff4s] def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy

}

private[ff4s] object VNode {

  def empty[F[_]](tag: String) = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy =
      Snabbdom.h(tag)
  }

  def parentNode[F[_]](tag: String, children: VNode[F]*) = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy =
      Snabbdom.h(
        tag,
        js.Dynamic.literal(),
        children.map(_.toSnabbdom(dispatcher)).toJSArray
      )
  }

  implicit def fromString[F[_]](text: String) = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy =
      VNodeProxy.fromString(text)
  }

  implicit class VNodeOps[F[_]](vnode: VNode[F]) {

    def withClass(cls: String): VNode[F] = setClass(vnode, cls)

    def withStyle(style: Map[String, String]): VNode[F] = setStyle(vnode)(style)

    def withProps(props: Map[String, Any]): VNode[F] = setProps(vnode)(props)

    def withAttrs(attrs: Map[String, DataObject.AttrValue]): VNode[F] =
      setAttrs(vnode)(attrs)

    def withKey(key: String): VNode[F] =
      setKey(vnode)(key)

    def withEventHandler(
        eventName: String,
        handler: dom.Event => F[Unit]
    ): VNode[F] =
      setEventHandler(vnode, eventName)(handler)

    def withOnInsertHook(onInsert: dom.Element => F[Unit]): VNode[F] =
      setOnInsertHook(vnode)((v: VNodeProxy) => onInsert(v.elm.get))

    def withDestroyHook(onDestroy: dom.Element => F[Unit]): VNode[F] =
      setDestroyHook(vnode)((v: VNodeProxy) => onDestroy(v.elm.get))

  }

  def setClass[F[_]](vnode: VNode[F], cls: String): VNode[F] = new VNode[F] {

    override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
      val vp = vnode.toSnabbdom(dispatcher)
      val data: DataObject = vp.data.toOption.getOrElse(DataObject.empty)
      data.attrs.toOption match {
        case None =>
          data.attrs = js.defined(js.Dictionary.apply("class" -> cls))
        case Some(attrs) => { attrs.update("class", cls) }
      }
      vp.data = data
      vp
    }

  }

  def setEventHandler[F[_]](vnode: VNode[F], eventName: String)(
      handler: dom.Event => F[Unit]
  ): VNode[F] = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
      val vp = vnode.toSnabbdom(dispatcher)
      val data: DataObject = vp.data.toOption.getOrElse(DataObject.empty)
      data.on.toOption match {
        case Some(on) =>
          on.update(
            eventName,
            ((e: dom.Event) => dispatcher.unsafeRunAndForget(handler(e)))
          )
        case None =>
          data.on = js.defined(
            js.Dictionary.apply(
              eventName -> ((e: dom.Event) =>
                dispatcher.unsafeRunAndForget(handler(e))
              )
            )
          )
      }
      vp.data = data
      vp
    }
  }

  def setKey[F[_]](vnode: VNode[F])(key: String): VNode[F] = new VNode[F] {

    override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
      val vp = vnode.toSnabbdom(dispatcher)
      val data: DataObject = vp.data.toOption.getOrElse(DataObject.empty)
      data.key = key
      vp.data = data
      vp.key =
        key // TODO: is this necessary? setting `key` on `data` object should be enough according to snabbdom docs
      vp
    }

  }

  def setOnInsertHook[F[_]](
      vnode: VNode[F]
  )(onInsert: VNodeProxy => F[Unit]): VNode[F] = new VNode[F] {

    override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
      val vp = vnode.toSnabbdom(dispatcher)
      val data: DataObject = vp.data.toOption.getOrElse(DataObject.empty)
      data.hook.toOption match {
        case None    => data.hook = Hooks.empty
        case Some(_) => ()
      }
      data.hook.get.insert = js.defined((n: VNodeProxy) =>
        dispatcher.unsafeRunAndForget(onInsert(n))
      )
      vp.data = data
      vp
    }

  }

  def setDestroyHook[F[_]](
      vnode: VNode[F]
  )(onDestroy: VNodeProxy => F[Unit]): VNode[F] = new VNode[F] {

    override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
      val vp = vnode.toSnabbdom(dispatcher)
      val data: DataObject = vp.data.toOption.getOrElse(DataObject.empty)
      data.hook.toOption match {
        case None    => data.hook = Hooks.empty
        case Some(_) => ()
      }
      data.hook.get.destroy = js.defined((n: VNodeProxy) =>
        dispatcher.unsafeRunAndForget(onDestroy(n))
      )
      vp.data = data
      vp
    }

  }

  def setProps[F[_]](vnode: VNode[F])(props: Map[String, Any]): VNode[F] =
    new VNode[F] {

      override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
        val vp = vnode.toSnabbdom(dispatcher)
        val data: DataObject = vp.data.toOption.getOrElse(DataObject.empty)
        data.props = js.defined(js.Dictionary(props.toSeq: _*))
        vp.data = data
        vp
      }

    }

  def setAttrs[F[_]](
      vnode: VNode[F]
  )(attrs: Map[String, DataObject.AttrValue]): VNode[F] =
    new VNode[F] {

      override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
        val vp = vnode.toSnabbdom(dispatcher)
        val data: DataObject = vp.data.toOption.getOrElse(DataObject.empty)
        data.attrs = js.defined(js.Dictionary(attrs.toSeq: _*))
        vp.data = data
        vp
      }

    }

  def setStyle[F[_]](vnode: VNode[F])(style: Map[String, String]): VNode[F] =
    new VNode[F] {

      override def toSnabbdom(dispatcher: Dispatcher[F]): VNodeProxy = {
        val vp = vnode.toSnabbdom(dispatcher)
        val data: DataObject = vp.data.toOption.getOrElse(DataObject.empty)
        data.style = js.defined(js.Dictionary(style.toSeq: _*))
        vp.data = data
        vp
      }

    }

}
