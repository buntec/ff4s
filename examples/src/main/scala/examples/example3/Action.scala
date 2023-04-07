package examples.example3

sealed trait Action

object Action {

  case class Inc() extends Action

  case class Dec() extends Action

  case class SetWeekday(weekday: Weekday) extends Action

}
