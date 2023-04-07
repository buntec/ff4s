/* Copyright 2022 buntec
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */

package ff4s

import com.raquo.domtypes.generic.defs.props.Props
import com.raquo.domtypes.generic.keys.Prop
import com.raquo.domtypes.generic.builders.canonical.CanonicalPropBuilder

trait PropDsl[F[_], State, Action] {
  self: ModifierDsl[F, State, Action] with Dsl[F, State, Action] =>

  trait PropsSyntax extends Props[Prop] with CanonicalPropBuilder {

    implicit class PropOps[V, DomV](prop: Prop[V, DomV]) {
      def :=(value: V): Modifier =
        Modifier.Prop[V, DomV](prop.name, value, prop.codec)
    }

  }
}
