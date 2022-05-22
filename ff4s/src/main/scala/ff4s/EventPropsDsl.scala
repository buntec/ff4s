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

      def :=(handler: Ev => Option[Action]): Modifier =
        Modifier.EventHandler(
          prop.name,
          (ev: dom.Event) => handler(ev.asInstanceOf[Ev])
        )

    }

  }
}
