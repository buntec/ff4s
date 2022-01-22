package com.github.buntec.ff4s.examples.minimal

import scala.concurrent.duration._

import org.scalajs.dom

import cats.syntax.all._
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.Random

import io.circe.generic.auto._

import com.github.buntec.ff4s

class App[F[_]: Async] {

  // Define our app's state space.
  case class State(
      name: Option[String] = None,
      pets: Pets = Cats,
      counter: Int = 0,
      bored: Option[Bored] = None,
      favoriteDish: Dish = Sushi,
      magic: Boolean = false,
      svgCoords: SvgCoords = SvgCoords(0, 0)
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

  // Define a set of actions.
  sealed trait Action
  case object Magic extends Action
  case class SetName(name: String) extends Action
  case class SetPets(pets: Pets) extends Action
  case class SetFavoriteDish(dish: Dish) extends Action
  case class IncrementCounter() extends Action
  case class DecrementCounter() extends Action
  case class GetActivity() extends Action
  case class SetSvgCoords(x: Double, y: Double) extends Action

  // Create a store by assigning actions to effects in F. (This is where we need `Async`.)
  implicit val store = for {
    store <- Resource.eval(ff4s.Store[F, State, Action](State()) {
      ref => (a: Action) =>
        a match {
          case SetSvgCoords(x, y) =>
            ref.update(_.copy(svgCoords = SvgCoords(x, y)))
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
            ff4s
              .HttpClient[F]
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
          // use `toString` here to avoid having to provide Eq typeclass instance
          (state.favoriteDish.toString, state.counter)
        }
        .changes
        .filter(p => p._1 == Ramen.toString && p._2 == 3)
        .evalMap(_ => store.dispatcher(Magic))
        .compile
        .drain
    )

    rng <- Resource.eval(Random.scalaUtilRandom)
    _ <- Async[F].background(
      fs2.Stream
        .fixedDelay(1.second)
        .evalMap { _ =>
          for {
            x <- rng.betweenDouble(10.0, 90.0)
            y <- rng.betweenDouble(10.0, 90.0)
          } yield (x, y)
        }
        .evalMap { case (x, y) => store.dispatcher(SetSvgCoords(x, y)) }
        .compile
        .drain
    )
  } yield store

  // Create a DSL for our model.
  val dsl = new ff4s.Dsl[F, State, Action]

  import dsl._ // basic dsl
  import dsl.syntax.html._ // nice syntax for html tags, attributes etc.

  val linkCls = "text-pink-500"
  val subHeadingCls = "text-center text-2xl mt-4 mb-2"

  // We can use literals for convenience, e.g., SVG icons
  val plusIcon = literal("""<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
<path fill-rule="evenodd" clip-rule="evenodd" d="M10 18C14.4183 18 18 14.4183 18 10C18 5.58172 14.4183 2 10 2C5.58172 2 2 5.58172 2 10C2 14.4183 5.58172 18 10 18ZM11 7C11 6.44772 10.5523 6 10 6C9.44772 6 9 6.44772 9 7V9H7C6.44772 9 6 9.44771 6 10C6 10.5523 6.44772 11 7 11H9V13C9 13.5523 9.44772 14 10 14C10.5523 14 11 13.5523 11 13V11H13C13.5523 11 14 10.5523 14 10C14 9.44772 13.5523 9 13 9H11V7Z" fill="#4A5568"/>
</svg>""") // taken from https://github.com/tailwindlabs/heroicons, MIT License
  val minusIcon = literal("""<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
<path fill-rule="evenodd" clip-rule="evenodd" d="M10 18C14.4183 18 18 14.4183 18 10C18 5.58172 14.4183 2 10 2C5.58172 2 2 5.58172 2 10C2 14.4183 5.58172 18 10 18ZM7 9C6.44772 9 6 9.44772 6 10C6 10.5523 6.44772 11 7 11H13C13.5523 11 14 10.5523 14 10C14 9.44772 13.5523 9 13 9H7Z" fill="#4A5568"/>
</svg>""") // taken from https://github.com/tailwindlabs/heroicons, MIT License

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
      ", leveraging the magical powers of ",
      a(cls := linkCls, href := "https://fs2.io", "FS2"),
      " and ",
      a(
        cls := linkCls,
        href := "https://typelevel.org/cats-effect",
        "Cats Effect"
      ),
      "."
    ),
    p(
      cls := "mt-4",
      "This is a minimal SPA built with ff4s. Please check out the code to see how it all works."
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
          cls := "text-center m-1 rounded font-light shadow",
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
    div(
      h2(cls := subHeadingCls, "A dropdown"),
      div(
        cls := "flex flex-col",
        p("Select your favorite dish."),
        select(
          cls := "m-1 rounded font-light",
          onChange := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLSelectElement =>
                Some(SetFavoriteDish(Dish.fromString(el.value).get))
              case _ => None
            }
          ),
          Dish.all.map(food =>
            option(
              (if (food == Sushi) defaultSelected := true else noop),
              key := food.toString,
              value := food.toString,
              food.toString
            )
          )
        ),
        span(s"Great choice, we like ${state.favoriteDish} too!")
      )
    )
  }

  val magicAlert = useState { state =>
    if (state.magic)
      div(
        span(cls := "text-xl", "✨You found the magic combination✨")
      )
    else empty
  }

  val radioButtons = useState { state =>
    div(
      cls := "m-1 flex flex-col items-center",
      h2(cls := subHeadingCls, "Radio buttons"),
      p("What is your favorite pet?"),
      div(
        Pets.all.map { pets =>
          label(
            cls := "m-1 flex flex-row items-center",
            input(
              cls := "m-1",
              tpe := "radio",
              checked := (state.pets == pets),
              onChange := (_ => Some(SetPets(pets)))
            ),
            pets.toString
          )
        }
      ),
      span(
        {
          state.pets match {
            case Cats => "Meow!"
            case Dogs => "Woof!"
          }
        }
      )
    )
  }

  val svgDemo = useState { state =>
    div(
      cls := "m-1 flex flex-col items-center",
      h2(cls := subHeadingCls, "A simple SVG"), {
        import dsl.syntax.svg._
        svg(
          height := "100",
          width := "100",
          circle(
            cx := "50",
            cy := "50",
            r := "10",
            stroke := "blue",
            fill := "transparent"
          ),
          rect(
            x := state.svgCoords.x.toString,
            y := state.svgCoords.y.toString,
            width := "30",
            height := "30",
            stroke := "black",
            fill := "transparent"
          )
        )
      }
    )
  }

  val app = div(
    cls := "flex flex-col items-center",
    welcome,
    magicAlert,
    intro,
    counter,
    apiCalls,
    textInput,
    dropDown,
    radioButtons,
    svgDemo
  )

  // this is our entry point
  def run: F[Nothing] = app.renderInto("#app")

}
