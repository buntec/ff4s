package com.github.buntec.ff4s.examples.minimal

import scala.concurrent.duration._

import org.scalajs.dom

import cats.syntax.all._

import cats.effect.kernel.{Async, Resource}

import io.circe.generic.auto._

import com.github.buntec.ff4s.{Dsl, Store}
import com.github.buntec.ff4s.HttpClient

class App[F[_]: Async] {

  // Define our app's state space.

  val defaultDish = "Pizza"

  case class State(
      name: Option[String] = None,
      pets: String = "cats",
      counter: Int = 0,
      bored: Option[Bored] = None,
      favoriteDish: String = defaultDish,
      magic: Boolean = false
  )
  case class Bored(activity: String, `type`: String)

  // Define a set of actions.
  sealed trait Action
  case object Magic extends Action
  case class SetName(name: String) extends Action
  case class SetPets(pets: String) extends Action
  case class SetFavoriteDish(dish: String) extends Action
  case class IncrementCounter() extends Action
  case class DecrementCounter() extends Action
  case class GetActivity() extends Action

  // Create a store by assigning actions to effects in F. (This is where we need `Async`.)
  implicit val store = for {
    store <- Resource.eval(Store[F, State, Action](State()) {
      ref => (a: Action) =>
        a match {
          case Magic => ref.update(_.copy(magic = true))
          case SetName(name) =>
            ref.update(
              _.copy(name = if (name.nonEmpty) Some(name) else None)
            )
          case SetPets(pets) =>
            ref.update(_.copy(pets = pets))
          case SetFavoriteDish(dish) => ref.update(_.copy(favoriteDish = dish))
          case IncrementCounter() =>
            ref.update(s => s.copy(counter = s.counter + 1))
          case DecrementCounter() =>
            ref.update(s => s.copy(counter = s.counter - 1))
          case GetActivity() =>
            HttpClient[F]
              .get[Bored]("https://www.boredapi.com/api/activity")
              .flatMap { bored =>
                ref.update(s => s.copy(bored = Some(bored)))
              }

        }
    })
    // We can do something fancy in the background...
    _ <- Async[F].background(
      (fs2.Stream.emit(()) ++ fs2.Stream.fixedDelay(5.second))
        .covary[F]
        .evalMap(_ => store.dispatcher(GetActivity()))
        .compile
        .drain
    )
    // We can also react to state changes...
    _ <- Async[F].background(
      store.state.discrete
        .map { state =>
          (state.favoriteDish, state.counter)
        }
        .changes
        .filter(p => p._1 == "Ramen" && p._2 == 17)
        .evalMap(_ => store.dispatcher(Magic))
        .compile
        .drain
    )
  } yield store

  // Create a DSL for our model.
  val dsl = new Dsl[F, State, Action]

  import dsl._ // basic dsl
  import dsl.syntax.html._ // nice syntax

  val linkCls = "text-pink-500"
  val subHeadingCls = "text-center text-2xl mt-5 mb-2"

  // We can use for-comprehensions to build our components since `View` is a (free) monad.
  val catsOrDogs = for {
    s <- getState
    catsRadio <- input(
      cls := "m-1",
      tpe := "radio",
      checked := (s.pets == "cats"),
      onChange := (_ => Some(SetPets("cats")))
    )
    cats <- div(cls := "m-1 flex flex-row", "cats", catsRadio)
    dogsRadio <- input(
      cls := "m-1",
      tpe := "radio",
      checked := (s.pets == "dogs"),
      onChange := (_ => Some(SetPets("dogs")))
    )
    dogs <- div(cls := "m-1 flex flex-row", "dogs", dogsRadio)
    node <- div(cls := "m-1 flex flex-col", cats, dogs)
  } yield node

  // We can use literals for convenience, e.g., SVG icons
  val plusIcon = literal("""<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
<path fill-rule="evenodd" clip-rule="evenodd" d="M10 18C14.4183 18 18 14.4183 18 10C18 5.58172 14.4183 2 10 2C5.58172 2 2 5.58172 2 10C2 14.4183 5.58172 18 10 18ZM11 7C11 6.44772 10.5523 6 10 6C9.44772 6 9 6.44772 9 7V9H7C6.44772 9 6 9.44771 6 10C6 10.5523 6.44772 11 7 11H9V13C9 13.5523 9.44772 14 10 14C10.5523 14 11 13.5523 11 13V11H13C13.5523 11 14 10.5523 14 10C14 9.44772 13.5523 9 13 9H11V7Z" fill="#4A5568"/>
</svg>""") // taken from https://github.com/tailwindlabs/heroicons, MIT License
  val minusIcon = literal("""<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
<path fill-rule="evenodd" clip-rule="evenodd" d="M10 18C14.4183 18 18 14.4183 18 10C18 5.58172 14.4183 2 10 2C5.58172 2 2 5.58172 2 10C2 14.4183 5.58172 18 10 18ZM7 9C6.44772 9 6 9.44772 6 10C6 10.5523 6.44772 11 7 11H13C13.5523 11 14 10.5523 14 10C14 9.44772 13.5523 9 13 9H7Z" fill="#4A5568"/>
</svg>""") // taken from https://github.com/tailwindlabs/heroicons, MIT License

  val mySvg = {
    import dsl.syntax.svg._
    svg(height := "100", width := "100")
  }

  val longList = useState { state =>
    div(
      cls := "bg-blue-400",
      key := "my-long-list",
      thunked := (s => s.name),
      Seq.tabulate(100) { i =>
        div(
          key := i,
          cls := "m-1 px-1 rounded flex flex-row",
          Seq.tabulate(10)(j => div(s"${state.name} ${i}/${j}"))
        )
      }
    )
  }

  val welcome = h1(
    cls := "m-4 text-4xl", // some tailwindcss classes
    "Welcome to ff4s!" // strings are valid child nodes
  )

  val intro = div(
    cls := "m-4 text-center",
    p(
      "ff4s lets you write web frontends in a purely functional style using ",
      a(
        cls := linkCls,
        href := "https://www.scala-js.org",
        "Scala.js"
      ),
      "."
    ),
    p(
      "Here we demonstrate some basic functionality of ff4s."
    )
  )

  val counter = useState { state =>
    val buttonClasses =
      "m-1 shadow bg-zinc-300 hover:bg-zinc-400 active:bg-zinc-500 text-white py-1 px-2 rounded"
    div(
      cls := "m-4",
      h2(cls := subHeadingCls, "A simple counter"),
      p(
        s"The counter stands at ${state.counter}. Click the buttons to increment or decrement the counter."
      ),
      div(
        cls := "flex flex-row justify-center",
        button(
          tpe := "button",
          cls := buttonClasses,
          onClick := (_ => Some(IncrementCounter())),
          plusIcon // can take other components as children
        ),
        button(
          tpe := "button",
          cls := buttonClasses,
          onClick := (_ => Some(DecrementCounter())),
          minusIcon
        )
      )
    )
  }

  val apiCalls = useState { state =>
    div(
      cls := "m-4",
      h2(cls := subHeadingCls, "API calls"),
      p(
        "We query ",
        a(cls := linkCls, "The Bored API", href := "https://www.boredapi.com"),
        " every few seconds in the background and display the result:"
      ),
      span(
        cls := "text-center",
        state.bored.fold(
          p(cls := "animate-pulse text-amber-600", "loading...")
        )(b => p(cls := "text-amber-600", s"${b.activity} (${b.`type`})"))
      )
    )
  }

  val textInput = useState { state =>
    div(
      h2(cls := subHeadingCls, "Text input"),
      p("Here is a simple text input."),
      div(
        cls := "flex flex-col",
        input(
          tpe := "text",
          cls := "m-1 rounded font-light shadow",
          placeholder := "type something here...",
          value := state.name.getOrElse(""),
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement => Some(SetName(el.value))
              case _                        => None
            }
          )
        ),
        span(
          cls := "text-center",
          s"You typed ${state.name.map(_.split("\\W+").length).getOrElse(0)} words."
        )
      )
    )
  }

  val dropDown = useState { state =>
    val foods = Seq("Sushi", "Pizza", "Pasta", "Ramen")
    div(
      h2(cls := subHeadingCls, "A dropdown"),
      div(
        cls := "flex flex-col",
        p("Select your favorite dish."),
        select(
          cls := "m-1 rounded font-light",
          onChange := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLSelectElement => Some(SetFavoriteDish(el.value))
              case _                         => None
            }
          ),
          foods.map(food =>
            option(
              (if (food == defaultDish) defaultSelected := true else noop),
              key := food,
              value := food,
              food
            )
          )
        ),
        span(s"Great choice, I like ${state.favoriteDish} too!")
      )
    )
  }

  val magicAlert = useState { state =>
    if (state.magic)
      div(
        span(cls := "text-xl", "You found the magic combination...")
      )
    else empty
  }

  val app = div(
    cls := "flex flex-col items-center",
    welcome,
    magicAlert,
    intro,
    counter,
    apiCalls,
    textInput,
    dropDown
  )

  // this is our entry point
  def run: F[Nothing] = app.renderInto("#app")

}
