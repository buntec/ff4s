package com.github.buntec.ff4s.snabbdom

/* Snabbdom facades taken verbatim from https://github.com/outwatch/outwatch */

import org.scalajs.dom._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.|
import scala.annotation.nowarn

trait Hooks extends js.Object {
  var init: js.UndefOr[Hooks.HookSingleFn] = js.undefined
  var insert: js.UndefOr[Hooks.HookSingleFn] = js.undefined
  var prepatch: js.UndefOr[Hooks.HookPairFn] = js.undefined
  var update: js.UndefOr[Hooks.HookPairFn] = js.undefined
  var postpatch: js.UndefOr[Hooks.HookPairFn] = js.undefined
  var destroy: js.UndefOr[Hooks.HookSingleFn] = js.undefined
}

object Hooks {
  type HookSingleFn = js.Function1[VNodeProxy, Unit]
  type HookPairFn = js.Function2[VNodeProxy, VNodeProxy, Unit]

  def empty: Hooks = new Hooks {}
}

trait DataObject extends js.Object {
  import DataObject._

  var attrs: js.UndefOr[js.Dictionary[AttrValue]] = js.undefined
  var props: js.UndefOr[js.Dictionary[PropValue]] = js.undefined
  var style: js.UndefOr[js.Dictionary[StyleValue]] = js.undefined
  var on: js.UndefOr[js.Dictionary[js.Function1[Event, Unit]]] = js.undefined
  var hook: js.UndefOr[Hooks] = js.undefined
  var key: js.UndefOr[KeyValue] = js.undefined
  var ns: js.UndefOr[String] = js.undefined
}

object DataObject {

  type PropValue = Any
  type AttrValue = String | Boolean | Double | Int
  type StyleValue = String | js.Dictionary[String]
  type KeyValue =
    String | Double | Int // https://github.com/snabbdom/snabbdom#key--string--number

  def empty: DataObject = new DataObject {}
}

// These are the original facades for snabbdom thunk. But we implement our own, so that for equality checks, the equals method is used.
// @js.native
// @JSImport("snabbdom/thunk", JSImport.Namespace, globalFallback = "thunk")
// object thunkProvider extends js.Object {
//   val default: thunkFunction = js.native
// }
// @js.native
// trait thunkFunction extends js.Any {
//   def apply(selector: String, renderFn: js.Function, argument: js.Array[Any]): VNodeProxy = js.native
//   def apply(selector: String, key: String, renderFn: js.Function, argument: js.Array[Any]): VNodeProxy = js.native
// }

object patch {

  private val p = Snabbdom.init(
    js.Array(
      SnabbdomClass,
      SnabbdomEventListeners,
      SnabbdomAttributes,
      SnabbdomProps,
      SnabbdomStyle
    )
  )

  def apply(firstNode: VNodeProxy, vNode: VNodeProxy): VNodeProxy =
    p(firstNode, vNode)

  def apply(firstNode: Element, vNode: VNodeProxy): VNodeProxy =
    p(firstNode, vNode)
}

trait VNodeProxy extends js.Object {
  var sel: js.UndefOr[String] = js.undefined
  var data: js.UndefOr[DataObject] = js.undefined
  var children: js.UndefOr[js.Array[VNodeProxy]] = js.undefined
  var elm: js.UndefOr[Element] = js.undefined
  var text: js.UndefOr[String] = js.undefined
  var key: js.UndefOr[DataObject.KeyValue] = js.undefined
  var listener: js.UndefOr[js.Any] = js.undefined

  var _id: js.UndefOr[Int] = js.undefined
  var _unmount: js.UndefOr[Hooks.HookSingleFn] = js.undefined
  var _update: js.UndefOr[js.Function1[VNodeProxy, Unit]] = js.undefined
  var _args: js.UndefOr[js.Array[Any] | Boolean] = js.undefined
}

object VNodeProxy {
  def fromString(string: String): VNodeProxy = new VNodeProxy {
    text = string
  }

  def updateInto(source: VNodeProxy, target: VNodeProxy): Unit = if (
    source ne target
  ) {
    target.sel = source.sel
    target.key = source.key
    target.data = source.data
    target.children = source.children
    target.text = source.text
    target.elm = source.elm
    target.listener = source.listener
    target._id = source._id
    target._unmount = source._unmount
  }

  def copyInto(source: VNodeProxy, target: VNodeProxy): Unit = if (
    source ne target
  ) {
    updateInto(source, target)
    target._update = source._update
    target._args = source._args
  }
}

@js.native
@JSImport("snabbdom", JSImport.Namespace, globalFallback = "snabbdom")
object Snabbdom extends js.Object {

  @nowarn("msg=never used")
  def init(
      args: js.Array[Any]
  ): js.Function2[Node | VNodeProxy, VNodeProxy, VNodeProxy] = js.native

  @nowarn("msg=never used")
  def h(sel: String): VNodeProxy = js.native

  @nowarn("msg=never used")
  def h(sel: js.Any, b: js.Any): VNodeProxy = js.native

  @nowarn("msg=never used")
  def h(sel: js.Any, b: js.Any, c: js.Any): VNodeProxy = js.native

}

@js.native
@JSImport("snabbdom", "classModule")
object SnabbdomClass extends js.Object {}

@js.native
@JSImport("snabbdom", "eventListenersModule")
object SnabbdomEventListeners extends js.Object {}

@js.native
@JSImport("snabbdom", "attributesModule")
object SnabbdomAttributes extends js.Object {}

@js.native
@JSImport("snabbdom", "propsModule")
object SnabbdomProps extends js.Object {}

@js.native
@JSImport("snabbdom", "styleModule")
object SnabbdomStyle extends js.Object {}

@js.native
@JSImport("snabbdom", JSImport.Default)
object tovnode extends js.Function1[Element, VNodeProxy] {
  def apply(element: Element): VNodeProxy = js.native
}
