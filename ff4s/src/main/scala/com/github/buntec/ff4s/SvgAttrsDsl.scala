package ff4s

import com.raquo.domtypes.generic.keys.SvgAttr
import com.raquo.domtypes.generic.builders.canonical.CanonicalSvgAttrBuilder
import com.raquo.domtypes.generic.defs.attrs.SvgAttrs

trait SvgAttrsDsl[F[_], State, Action] { self: ModifierDsl[F, State, Action] =>

  trait SvgAttrsSyntax extends SvgAttrs[SvgAttr] with CanonicalSvgAttrBuilder {

    implicit class SvgAttrsOps[V](attr: SvgAttr[V]) {
      def :=(value: V): Modifier =
        Modifier.SvgAttr[V](attr.name, value, attr.codec)
    }

  }
}
