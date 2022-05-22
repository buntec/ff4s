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

import com.raquo.domtypes.generic.keys.HtmlAttr
import com.raquo.domtypes.generic.keys.Prop

import com.raquo.domtypes.generic.defs.attrs.HtmlAttrs
import com.raquo.domtypes.generic.defs.reflectedAttrs.ReflectedHtmlAttrs
import com.raquo.domtypes.generic.defs.complex.canonical.CanonicalComplexHtmlKeys

import com.raquo.domtypes.generic.builders.PropBuilder
import com.raquo.domtypes.generic.builders.canonical.CanonicalHtmlAttrBuilder
import com.raquo.domtypes.generic.builders.canonical.CanonicalReflectedHtmlAttrBuilder

trait HtmlAttrsDsl[F[_], State, Action] { self: ModifierDsl[F, State, Action] =>

  trait HtmlAttrsSyntax
      extends HtmlAttrs[HtmlAttr]
      with ReflectedHtmlAttrs[CanonicalReflectedHtmlAttrBuilder.ReflectedAttr]
      with CanonicalComplexHtmlKeys[
        CanonicalReflectedHtmlAttrBuilder.ReflectedAttr,
        HtmlAttr,
        Prop
      ]
      with CanonicalHtmlAttrBuilder
      with CanonicalReflectedHtmlAttrBuilder { self: PropBuilder[Prop] =>

    implicit class HtmlAttrsOps[V](attr: HtmlAttr[V]) {
      def :=(value: V): Modifier =
        Modifier.HtmlAttr(attr.name, value, attr.codec)
    }

  }
}
