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

import com.raquo.domtypes.generic.keys.Style
import com.raquo.domtypes.generic.defs.styles.{Styles, Styles2}
import com.raquo.domtypes.generic.builders.StyleBuilders

trait StyleDsl[F[_], State, Action] {
  self: ModifierDsl[F, State, Action] with Dsl[F, State, Action] =>

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
