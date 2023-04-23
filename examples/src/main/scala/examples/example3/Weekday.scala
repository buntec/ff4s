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

package examples.example3

import cats.Show
import cats.syntax.all._

sealed trait Weekday
object Weekday {

  case object Monday extends Weekday
  case object Tuesday extends Weekday
  case object Wednesday extends Weekday
  case object Thursday extends Weekday
  case object Friday extends Weekday
  case object Saturday extends Weekday
  case object Sunday extends Weekday

  def fromString(s: String): Option[Weekday] = s match {
    case "Monday"    => Monday.some
    case "Tuesday"   => Tuesday.some
    case "Wednesday" => Wednesday.some
    case "Thursday"  => Thursday.some
    case "Friday"    => Friday.some
    case "Saturday"  => Saturday.some
    case "Sunday"    => Sunday.some
    case _           => none
  }

  implicit val show: Show[Weekday] = Show.fromToString

  val values = List(
    Monday,
    Tuesday,
    Wednesday,
    Thursday,
    Friday,
    Saturday,
    Sunday
  )

}
