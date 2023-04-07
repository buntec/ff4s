package examples.example3

import cats.syntax.all._
import cats.Show

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
