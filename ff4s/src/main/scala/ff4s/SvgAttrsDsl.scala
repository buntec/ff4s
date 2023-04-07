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

import com.raquo.domtypes.generic.keys.SvgAttr
import com.raquo.domtypes.generic.builders.canonical.CanonicalSvgAttrBuilder
import com.raquo.domtypes.generic.defs.attrs.SvgAttrs

trait SvgAttrsDsl[F[_], State, Action] {
  self: ModifierDsl[F, State, Action] with Dsl[F, State, Action] =>

  trait SvgAttrsSyntax extends SvgAttrs[SvgAttr] with CanonicalSvgAttrBuilder {

    implicit class SvgAttrsOps[V](attr: SvgAttr[V]) {
      def :=(value: V): Modifier =
        Modifier.SvgAttr[V](attr.name, value, attr.codec)
    }

  }
}
