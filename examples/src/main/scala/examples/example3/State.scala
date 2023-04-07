package examples.example3

case class State(
    weekday: Weekday = Weekday.Monday,
    counter: Int = 0,
    buttonOff: Boolean = false
)
