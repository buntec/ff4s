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
