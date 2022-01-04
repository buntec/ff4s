package com.github.buntec.ff4s

import com.raquo.domtypes.generic.keys.Style
import com.raquo.domtypes.generic.defs.styles.{Styles, Styles2}
import com.raquo.domtypes.generic.builders.StyleBuilders

trait StyleDsl[F[_], State, Action] { self: ModifierDsl[F, State, Action] =>

  trait StylesSyntax
      extends Styles[Modifier]
      with Styles2[Modifier]
      with StyleBuilders[Modifier] {

    implicit class StyleOps[V](style: Style[V]) {
      def :=(value: V): Modifier =
        Modifier.Style(style.name, value.toString)
    }

    override protected def buildIntStyleSetter(
        style: Style[Int],
        value: Int
    ): Modifier = style := value

    override protected def buildDoubleStyleSetter(
        style: Style[Double],
        value: Double
    ): Modifier = style := value

    override protected def buildStringStyleSetter(
        style: Style[_],
        value: String
    ): Modifier = Modifier.Style(style.name, value)

  }

}
