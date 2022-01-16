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
  case class State(
      name: Option[String],
      pets: String,
      counter: Int,
      bored: Option[Bored]
  )
  case class Bored(activity: String, `type`: String)
  val init = State(None, "cats", 0, None)

  // Define a set of actions.
  sealed trait Action
  case class SetName(name: String) extends Action
  case class SetPets(pets: String) extends Action
  case class IncrementCounter() extends Action
  case class DecrementCounter() extends Action
  case class GetActivity() extends Action

  // Create a store by assigning actions to effects in F. (This is where we need `Async`.)
  implicit val store = for {
    store <- Resource.eval(Store[F, State, Action](init) { ref => (a: Action) =>
      a match {
        case SetName(name) =>
          ref.update(
            _.copy(name = if (name.nonEmpty) Some(name.toUpperCase) else None)
          )
        case SetPets(pets) =>
          ref.update(_.copy(pets = pets))
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
      fs2.Stream
        .fixedDelay(1.second)
        .covary[F]
        .evalMap(_ => store.dispatcher(GetActivity()))
        .compile
        .drain
    )
  } yield store

  // Create a DSL for our model.
  val dsl = new Dsl[F, State, Action]

  import dsl._ // basic dsl
  import dsl.syntax.html._ // nice syntax

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

  val buttonClasses =
    "m-1 bg-indigo-400 hover:bg-indigo-600 text-white py-1 px-2 rounded"

  // Monadic flow is optional - we can also pass components as children directly
  val incButton = button(
    tpe := "button",
    cls := buttonClasses,
    onClick := (_ => Some(IncrementCounter())),
    plusIcon // can take other components as children
  )

  val decButton = button(
    tpe := "button",
    cls := buttonClasses,
    onClick := (_ => Some(DecrementCounter())),
    minusIcon
  )

  val counterButtons = div(cls := "flex flex-row", incButton, decButton)

  val mySvg = {
    import dsl.syntax.svg._
    svg(height := "100", width := "100")
  }

  val longList = getState.flatMap { state =>
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

  val view = for {
    s <- getState
    welcome <- h1(
      cls := "text-xl text-gray-900", // some tailwindcss classes
      "Welcome to ff4s!" // strings are valid child nodes
    )
    inputHandler = (ev: dom.Event) =>
      ev.target match {
        case el: dom.HTMLInputElement => Some(SetName(el.value))
        case _                        => None
      }
    userInput <- input( // a simple text input
      tpe := "text",
      cls := "m-1",
      placeholder := "Your name here...",
      onInput := inputHandler,
      value := s.name.getOrElse("")
    )
    greeting <- p(
      cls := "m-1",
      s.name.fold("Please enter your name")(name =>
        s"Hello, $name! Your favorite pets are ${s.pets}."
      )
    )
    counter <- p(s"The counter stands at ${s.counter}")
    suggestedActivity <- s.bored.fold(empty)(b =>
      p(s"${b.activity} (${b.`type`})")
    )
    node <- div(
      cls := "flex flex-col items-center",
      backgroundColor := "lightgray", // a style attribute
      welcome, // child nodes...
      greeting,
      userInput,
      catsOrDogs,
      suggestedActivity,
      counter,
      counterButtons,
      mySvg,
      longList
    )
  } yield node

  // this is our entry point
  def run: F[Nothing] = view.renderInto("#app")

}
