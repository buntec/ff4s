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

import cats.effect.Concurrent
import cats.effect.Resource
import cats.syntax.all._
import ff4s.Store

import monocle.syntax.all._
import cats.Show

// This example shows how to create re-usable components.
case class State(
    weekday: Weekday = Weekday.Monday,
    counter: Int = 0,
    buttonOff: Boolean = false
)

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

sealed trait Action

object Action {

  case class Inc() extends Action

  case class Dec() extends Action

  case class SetWeekday(weekday: Weekday) extends Action

}

class App[F[_]](implicit val F: Concurrent[F])
    extends ff4s.App[F, State, Action] {

  val store: Resource[F, Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { ref =>
      _ match {
        case Action.SetWeekday(weekday) =>
          ref.update(_.focus(_.weekday).replace(weekday))
        case Action.Inc() =>
          ref.update(_.focus(_.counter).modify(_ + 1))
        case Action.Dec() =>
          ref.update(_.focus(_.counter).modify(_ - 1))
      }
    }

  val components = new Components[F, State, Action]
  import components._

  import dsl._
  import dsl.syntax.html._

  val root = useState { state =>
    div(
      cls := "bg-zinc-200 h-screen flex flex-col items-center justify-center gap-2",
      counter(_.counter, _ => Action.Inc(), _ => Action.Dec()),
      labeledSelect[Weekday](
        "weekday",
        (s: String) => Weekday.fromString(s),
        (_, l) => Action.SetWeekday(l).some,
        Weekday.values,
        _.weekday
      ),
      span(s"Weekday: ${state.weekday.show}")
    )
  }

}
