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

package examples.example2

// Define our app's state space.
case class State(
    name: Option[String] = None,
    pets: Pets = Cats,
    counter: Int = 0,
    bored: Option[Bored] = None,
    favoriteDish: Dish = Sushi,
    magic: Boolean = false,
    svgCoords: SvgCoords = SvgCoords(0, 0),
    websocketResponse: Option[String] = None
)

case class SvgCoords(x: Double, y: Double)

case class Bored(activity: String, `type`: String)

sealed trait Dish
case object Sushi extends Dish
case object Pizza extends Dish
case object Pasta extends Dish
case object Ramen extends Dish

object Dish {

  val all: Seq[Dish] = Seq(Sushi, Pizza, Pasta, Ramen)

  def fromString(s: String): Option[Dish] = s match {
    case "Sushi" => Some(Sushi)
    case "Pizza" => Some(Pizza)
    case "Pasta" => Some(Pasta)
    case "Ramen" => Some(Ramen)
    case _       => None
  }

}

sealed trait Pets
case object Cats extends Pets
case object Dogs extends Pets

object Pets {

  val all: Seq[Pets] = Seq(Cats, Dogs)

}
