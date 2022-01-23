package com.github.buntec.ff4s.examples.minimal

import scala.concurrent.duration._

import org.scalajs.dom

import cats.syntax.all._
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.Random

import io.circe.parser._
import io.circe.generic.auto._

import com.github.buntec.ff4s
import cats.effect.kernel.Fiber

// This is a small demo application so show off the basic functionality of ff4s.
// It uses tailwindcss for simple styling.
//
// In a real-world project, the code would be split over several files.
// Typically, we would have one file for defining the state types,
// one file for defining the actions, one file for for the store,
// and several files for the view components.
//
// Note that we use the "tagless final" style where `F` is a generic effect type.
// We always require an instance of `cats.effect.Async` for `F`.
class App[F[_]: Async] {

  // Define our app's state space.
  case class State(
      name: Option[String] = None,
      pets: Pets = Cats,
      counter: Int = 0,
      bored: Option[Bored] = None,
      favoriteDish: Dish = Sushi,
      magic: Boolean = false,
      svgCoords: SvgCoords = SvgCoords(0, 0),
      bitcoinPrice: Option[Double] = None,
      websocketFiber: Option[Fiber[F, Throwable, Unit]] = None
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
  case object IncrementCounter extends Action
  case object DecrementCounter extends Action
  case object GetActivity extends Action
  case class SetSvgCoords(x: Double, y: Double) extends Action
  case object StartWebsocket extends Action
  case object StopWebsocket extends Action

  // Create a store by assigning actions to effects in F.
  implicit val store = for {
    store <- Resource.eval(ff4s.Store[F, State, Action](State()) {
      ref => (a: Action) =>
        a match {
          case StopWebsocket =>
            ref.get.flatMap { state =>
              state.websocketFiber.fold(Async[F].unit)(_.cancel)
            }
          case StartWebsocket =>
            for {
              fiber <- Async[F].start(
                ff4s
                  .WebSocketsClient[F]
                  .stream(
                    "wss://ws.bitmex.com/realtime?subscribe=instrument:XBTUSD",
                    is =>
                      is.evalMap { msg =>
                        (for {
                          json <- Async[F].fromEither(parse(msg))
                          lastPrice <- Async[F].fromEither(
                            json.hcursor
                              .downField("data")
                              .downArray
                              .downField("lastPrice")
                              .as[Double]
                          )
                          _ <- ref
                            .update(_.copy(bitcoinPrice = Some(lastPrice)))
                        } yield ()).handleError(_ => ())
                      }.drain
                  )
              )
              sPrev <- ref.getAndUpdate(
                _.copy(
                  websocketFiber = Some(fiber)
                )
              )
              _ <- sPrev.websocketFiber.fold(Async[F].unit)(_.cancel)
            } yield ()
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
          case IncrementCounter =>
            ref.update(s => s.copy(counter = s.counter + 1))
          case DecrementCounter =>
            ref.update(s => s.copy(counter = s.counter - 1))
          case GetActivity =>
            // ff4s provides a very basic HTTP client (currently using sttp under the hood).
            ff4s
              .HttpClient[F]
              .get[Bored]("https://www.boredapi.com/api/activity")
              .flatMap { bored =>
                ref.update(s => s.copy(bored = Some(bored)))
              }

        }
    })
    // We can do something fancy in the background.
    _ <- Async[F].background(
      (fs2.Stream.emit(()) ++ fs2.Stream.fixedDelay(5.second))
        .covary[F]
        .evalMap(_ => store.dispatcher(GetActivity))
        .compile
        .drain
    )
    // We can also listen to and react to state changes.
    _ <- Async[F].background(
      store.state.discrete
        .map { state =>
          // Use `toString` here to avoid having to provide a Eq typeclass instance.
          (state.favoriteDish.toString, state.counter)
        }
        .changes
        .filter(p => p._1 == Ramen.toString && p._2 == 3)
        .evalMap(_ => store.dispatcher(Magic))
        .compile
        .drain
    )
    // Animate our SVG with some random numbers.
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

  // Define some classes for easy re-use.
  val linkCls = "text-pink-500"
  val subHeadingCls = "text-center text-2xl mt-4 mb-2"

  // We can use (unsafe!) literals for convenience, e.g., SVG icons
  val plusIcon = literal("""<svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v3m0 0v3m0-3h3m-3 0H9m12 0a9 9 0 11-18 0 9 9 0 0118 0z" />
</svg>""") // taken from https://github.com/tailwindlabs/heroicons, MIT License
  val minusIcon = literal(
    """<svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12H9m12 0a9 9 0 11-18 0 9 9 0 0118 0z" />
</svg>"""
  ) // taken from https://github.com/tailwindlabs/heroicons, MIT License

  // The html syntax import gives us a nice DSL for composing HTML markup.
  // We can easily nest elements inside each other, define attributes,
  // properties, event handlers etc.
  val welcome =
    h1( // All common html tags are available thanks to scala-dom-types.
      cls := "m-4 text-4xl", // Some tailwindcss utility classes.
      "Welcome to ff4s 👋" // Strings are valid child nodes, of course.
    )

  val intro = div(
    cls := "m-4 text-center",
    p(
      "ff4s lets you write web front-ends in a purely functional style using ",
      a(
        cls := linkCls,
        href := "https://www.scala-js.org", // All common html attributes are available thanks to scala-dom-types.
        "Scala.js"
      ),
      " and the magical powers of ",
      a(cls := linkCls, href := "https://fs2.io", "FS2"),
      " and ",
      a(
        cls := linkCls,
        href := "https://typelevel.org/cats-effect",
        "Cats Effect"
      ),
      ". It comes with an expressive and type-safe DSL for HTML/SVG markup thanks to the amazing ",
      a(
        cls := linkCls,
        href := "https://github.com/raquo/scala-dom-types",
        "Scala DOM Types"
      ),
      ". Rendering is based on the ",
      a(
        cls := linkCls,
        href := "https://github.com/snabbdom/snabbdom",
        "Snabbdom"
      ),
      " virtual DOM."
    ),
    p(
      cls := "mt-4",
      "This is a minimal SPA built with ff4s. Please check out the ",
      a(cls := linkCls, href := "https://github.com/buntec/ff4s", "code"),
      " to see how it all works."
    )
  )

  // If a component requires access to state, we can use `useState{ state => ...}`,
  // which is just an alias for `getState.flatMap{ state => ...}`.
  val counter = useState { state =>
    val buttonClasses =
      "m-1 shadow bg-emerald-500 text-zinc-200 hover:bg-emerald-600 active:bg-emerald-700 py-1 px-2 rounded"
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
          // We can assign callbacks to events; note that the callback
          // returns an `Option[Action]`, which, when defined, is dispatched
          // by our store. Returning `None` means we don't do anything.
          onClick := (_ => Some(IncrementCounter)),
          plusIcon
        ),
        button(
          tpe := "button",
          cls := buttonClasses,
          onClick := (_ => Some(DecrementCounter)),
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
          onInput := (
            // Callbacks are invoked with an instance of `dom.Event`.
            // We typically have to match on the type of the target to get the desired information.
            (ev: dom.Event) =>
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
          // `Seq`s of components are also valid child elements.
          // In this case one should always define the `key` attribute
          // to something that is unique among all siblings.
          Dish.all.map(food =>
            option(
              (if (food == Sushi) defaultSelected := true else noop),
              key := food.toString, // Should be unique among siblings.
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
    // The `empty` component can be convenient
    // for conditional rendering as it doesn't result in any element
    // being added to the DOM.
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
        // Because of name clashes with HTML/CSS we need a
        // separate import for SVG tags and attributes.
        // Note the curly braces defining a new scope.
        // Html syntax can still be accessed through fully-qualified names.
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

  val websocketExample = useState { state =>
    val btnCls =
      "m-1 font-light shadow bg-emerald-500 hover:bg-emerald-600 active:bg-emerald-700 text-zinc-100 py-1 px-2 rounded"
    div(
      cls := "m-1 flex flex-col items-center",
      h2(cls := subHeadingCls, "A WebSocket example"),
      p("Here we stream the latest bitcoin prices from a WebSocket API."),
      div(
        cls := "flex flex-row justify-center items-center",
        button(
          cls := btnCls,
          tpe := "button",
          "Start",
          onClick := (_ => Some(StartWebsocket))
        ),
        button(
          cls := btnCls,
          tpe := "button",
          "Stop",
          onClick := (_ => Some(StopWebsocket))
        )
      ),
      state.bitcoinPrice.fold(empty)(price =>
        span(cls := "m-2 text-xl text-amber-600", s"$price USD")
      )
    )
  }

  // Pull everything together into our final app.
  val app = div(
    cls := "mb-16 flex flex-col items-center",
    welcome,
    magicAlert,
    intro,
    counter,
    apiCalls,
    textInput,
    dropDown,
    radioButtons,
    svgDemo,
    websocketExample
  )

  // Render our app into the unique element with ID "app",
  // which is defined in our `index.html`.
  def run: F[Nothing] = app.renderInto("#app")

}
