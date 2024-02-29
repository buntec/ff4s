# Reusable Components

The ability to factor out and reuse components, possibly across project boundaries,
is an essential feature of any UI framework or library.
To accomplish this in ff4s we have to work with generic state and action types.
Here we illustrate the general approach with some toy examples.

We recommend organizing components into generic traits with a `Dsl` self-type:

```scala mdoc:js:shared
import cats.*
import cats.syntax.all.*
import org.scalajs.dom

// S and A are the state and action types.
trait Selects[S, A]:
  self: ff4s.Dsl[S, A] =>

  def customSelect[O: Show: Eq](
      onChange: O => Option[A],
      options: List[O],
      selected: Option[O]
  ): V =
    import html.{onChange => _, selected => _, *}
    select(
      cls := "custom-select",
      html.onChange := ((ev: dom.Event) =>
        val target = ev.target.asInstanceOf[dom.HTMLSelectElement]
        options.find(o => Show[O].show(o) == target.value).flatMap(onChange)
      ),
      options.map(name =>
        html.option(
          html.selected := (selected.exists(_ == name)),
          key := name.show,
          html.value := name.show,
          name.show
        )
      )
    )

trait Buttons[S, A]:
  self: ff4s.Dsl[S, A] =>

  def customButton(
      child: V,
      onClick: Option[A],
      isDisabled: Boolean
  ): V =
    import html.{onClick => _, *}
    button(
      cls := "custom-button",
      child,
      disabled := isDisabled,
      html.onClick := (_ => onClick)
    )
```


```scala mdoc:js:shared
case class State(counter: Int = 0, fruit: Fruit = Fruit.Banana)
```

To show off our custom selection component we define an enumeration with the required `Eq` and `Show` instances.

```scala mdoc:js:shared
import cats.Show
import cats.kernel.Eq

enum Fruit:
  case Apple, Banana, Orange, Strawberry

object Fruit:
  val all = List(Apple, Banana, Orange, Strawberry)
  given Show[Fruit] = Show.fromToString
  given Eq[Fruit] = Eq.fromUniversalEquals
```

```scala mdoc:js:shared
enum Action:
  case SetFruit(fruit: Fruit)
  case Inc
```

We omit the straightforward definition of the store.

With concrete state and action types we can instantiate our components and build the view.

```scala mdoc:js:shared
import cats.syntax.all.*

trait View extends Selects[State, Action] with Buttons[State, Action]:
  dsl: ff4s.Dsl[State, Action] =>

  import html.*

  val view =
    useState(state =>
      div(
        customSelect[Fruit](
          fruit => Action.SetFruit(fruit).some,
          Fruit.all,
          state.fruit.some
        ),
        div(s"Selected fruit: ${state.fruit}"),
        customButton(
          span("Increment"),
          Action.Inc.some,
          state.counter == 10
        ),
        if state.counter == 10 then
          div(
            styleAttr := "color: red",
            "Button disabled!"
          )
        else div(s"${10 - state.counter} increments remaining!")
      )
    )
```


```scala mdoc:js:invisible
import cats.effect.*

object Store:

  def apply[F[_]](using
      F: Async[F]
  ): Resource[F, ff4s.Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()): _ =>
      case (Action.SetFruit(fruit), state) =>
        state.copy(fruit = fruit) -> F.unit
      case (Action.Inc, state) =>
        state.copy(counter = state.counter + 1) -> F.unit

class App[F[_]](implicit F: Async[F])
    extends ff4s.App[F, State, Action]
    with View:
  override val store = Store[F]
  override val rootElementId = node.getAttribute("id")

new ff4s.IOEntryPoint(new App, false).main(Array())
```
