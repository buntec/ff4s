# Reusable Components

Reusable UI components are crucial in frontend development allowing developers to impose style uniformity and avoid code repeatability.
Button and select components are two of the most popular UI components and will be used in this example.

## Components

Custom select and button components are implemented as member methods of a `Components` class
with type parameters `F` for the effect type, `S` for the state type and `A` for the action type.

<!---->
<!-- ### Select -->
<!---->
<!-- A select component with options of type `O` is modelled by a function with the following inputs: -->
<!---->
<!-- 1. `fromString`: converts a string option to type `O`. -->
<!-- 2. `onChange0`: performs an action based on selected option and state. -->
<!-- 3. `options`: list of options. -->
<!-- 4. `selected0`: currently selected option function of the state. -->
<!---->
<!-- ### Button -->
<!---->
<!-- Likewise for a button component inputs: -->
<!---->
<!-- 1. `onClick0`: react to click events by performing an action. -->
<!-- 2. `isDisabled`: disable the button based on the state. -->
<!---->

```scala mdoc:js:shared
import org.scalajs.dom
import cats.Show
import cats.kernel.Eq
import cats.syntax.all._

class Components[F[_], S, A] {

  def customSelect[O: Show: Eq](
      fromString: String => Option[O],
      onChange: (S, O) => Option[A],
      options: List[O],
      selected: S => O
  )(implicit dsl: ff4s.Dsl[F, S, A]): dsl.V = {
    import dsl._
    import dsl.html.{onChange => onChange0, selected => selected0, _}

    useState { state =>
      select(
        onChange0 := ((ev: dom.Event) =>
          ev.target match {
            case el: dom.HTMLSelectElement =>
              fromString(el.value).flatMap(onChange(state, _))
            case _ => None
          }
        ),
        options.map { name =>
          option(
            selected0 := (name === selected(state)),
            key := name.show,
            value := name.show,
            name.show
          )
        }
      )
    }

  }

  def customButton(
      dsl: ff4s.Dsl[F, S, A]
  )(child: dsl.V, onClick: S => Option[A], isDisabled: S => Boolean): dsl.V = {
    import dsl._
    import dsl.html.{onClick => onClick0, _}

    useState { state =>
      button(
        child,
        disabled := isDisabled(state),
        onClick0 := (_ => onClick(state))
      )
    }
  }

}
```

Note that an instance of `ff4s.Dsl` is passed explicitly to the `customButton` function
as the `child` argument type depends explicitly on it, contrary to the select component example where
the instance is passed as an implicit.

## State

```scala mdoc:js:shared
final case class State(counter: Int = 0, fruit: Fruit = Fruit.Banana)
```

```scala mdoc:js:shared
import cats.Show
import cats.kernel.Eq

sealed trait Fruit

object Fruit {
  case object Apple extends Fruit
  case object Banana extends Fruit
  case object Orange extends Fruit
  case object Strawberry extends Fruit

  def fromString(s: String): Option[Fruit] = s match {
    case "Apple"      => Apple.some
    case "Banana"     => Banana.some
    case "Orange"     => Orange.some
    case "Strawberry" => Strawberry.some
    case _            => none
  }

  val all = List(Apple, Banana, Orange, Strawberry)

  implicit val show: Show[Fruit] = Show.show {
    case Apple      => "Apple"
    case Banana     => "Banana"
    case Orange     => "Orange"
    case Strawberry => "Strawberry"
  }

  implicit val eq: Eq[Fruit] = Eq.fromUniversalEquals
}
```

## Actions

```scala mdoc:js:shared
sealed trait Action
case class SetFruit(fruit: Fruit) extends Action
case object Inc extends Action
```

## Store

The construction of the store is straightforward and omitted for brevity.

## View

```scala mdoc:js:shared
import cats.syntax.all._

object View {

  def apply[F[_]](implicit dsl: ff4s.Dsl[F, State, Action]) = {

    import dsl._
    import dsl.html._

    val components = new Components[F, State, Action]
    import components._

    useState { state =>
      div(
        customSelect[Fruit](
          Fruit.fromString,
          (_, fruit) => SetFruit(fruit).some,
          Fruit.all,
          _.fruit
        ),
        div(s"Selected fruit: ${state.fruit}"),
        customButton(dsl)(
          span("Increment"),
          _ => Inc.some,
          _.counter == 10
        ),
        if (state.counter == 10)
          div(
            styleAttr := "color: red",
            "Button disabled!"
          )
        else div(s"${10 - state.counter} increments remaining!")
      )
    }

  }
}
```

## App

The construction of `ff4s.App` and `ff4s.IOEntryPoint` is straightforward and omitted for brevity.

```scala mdoc:js:invisible
import cats.effect._
import cats.syntax.all._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { _ =>
      _ match {
        case SetFruit(fruit) => _.copy(fruit = fruit) -> none
        case Inc => state => state.copy(counter = state.counter + 1) -> none
      }
    }
}

class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
