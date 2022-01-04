package com.github.buntec.ff4s

import com.raquo.domtypes.generic.defs.props.Props
import com.raquo.domtypes.generic.keys.Prop
import com.raquo.domtypes.generic.builders.canonical.CanonicalPropBuilder

trait PropDsl[F[_], State, Action] { self: ModifierDsl[F, State, Action] =>

  trait PropsSyntax extends Props[Prop] with CanonicalPropBuilder {

    implicit class PropOps[V, DomV](prop: Prop[V, DomV]) {
      def :=(value: V): Modifier =
        Modifier.Prop[V, DomV](prop.name, value, prop.codec)
    }

  }
}
