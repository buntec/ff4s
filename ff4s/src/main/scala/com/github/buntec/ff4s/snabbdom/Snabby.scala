package com.github.buntec.ff4s.snabbdom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.annotation.nowarn

@js.native
@JSImport("snabby", JSImport.Default)
object Snabby extends js.Object {

  @nowarn("msg=never used")
  def apply(strings: js.Array[Any], values: Any*): VNodeProxy = js.native

}
