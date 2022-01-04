package com.github.buntec.ff4s

import org.scalajs.dom
import com.raquo.domtypes.jsdom.defs.eventProps._
import com.raquo.domtypes.generic.builders.canonical.CanonicalEventPropBuilder
import com.raquo.domtypes.generic.keys.EventProp

trait EventPropsDsl[F[_], State, Action] {
  self: ModifierDsl[F, State, Action] =>

  trait EventPropsSyntax
      extends CanonicalEventPropBuilder[dom.Event]
      with FormEventProps[EventProp]
      with MouseEventProps[EventProp]
      with KeyboardEventProps[EventProp] {

    implicit class EvenPropOps[Ev](prop: EventProp[Ev]) {

      def :=(handler: dom.Event => Option[Action]): Modifier =
        Modifier.EventHandler(prop.name, handler)

    }

  }
}
