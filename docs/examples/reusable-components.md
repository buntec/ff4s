# Reusable Components

The ability to factor out and reuse components, possibly across project boundaries, is an essential feature of any UI framework or library. To accomplish this in ff4s we have to work with generic state and action types. Here we illustrate the general approach with some toy examples.

We recommend organizing components into generic traits:

```scala mdoc:js:shared
import org.scalajs.dom
import cats._
import cats.syntax.all._

// S and A are the state and action types, respectively
trait Selects[F[_], S, A] {

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
// more ...

}

trait Buttons[F[_], S, A] {
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

// more ...

}
```

Unfortunately, if a component takes another component as a parameter, then `dsl` cannot be passed implicitly due to dependent typing.

```scala mdoc:js:shared
final case class State(counter: Int = 0, fruit: Fruit = Fruit.Banana)
```

To show off our custom selection component we define an enumeration with the required `Eq` and `Show` instances.

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

```scala mdoc:js:shared
sealed trait Action
case class SetFruit(fruit: Fruit) extends Action
case object Inc extends Action
```

We omit the straightforward definition of the store.

With concrete state and action types we can instantiate our components and build the view.

```scala mdoc:js:shared
import cats.syntax.all._

object View extends {

  def apply[F[_]](implicit dsl: ff4s.Dsl[F, State, Action]) = {

    import dsl._
    import dsl.html._

    object components
        extends Selects[F, State, Action]
        with Buttons[F, State, Action]
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
