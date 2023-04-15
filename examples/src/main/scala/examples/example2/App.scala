/*
 * Copyright 2022 buntec
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package examples.example2

import cats.effect.Async
import org.scalajs.dom

// This is a small demo SPA showcasing the basic functionality of ff4s.
// In a real-world project the components likely would be split across several files.
class App[F[_]: Async] extends ff4s.App[F, State, Action] {

  val store = Store[F]

  import dsl._ // basic dsl
  import dsl.html._ // nice syntax for html tags, attributes etc.

  // Define some classes for easy re-use.
  val linkCls = "text-pink-500"
  val subHeadingCls = "text-center text-2xl mt-4 mb-2"
  val buttonCls =
    "m-1 shadow bg-emerald-500 text-zinc-200 hover:bg-emerald-600 active:bg-emerald-700 py-1 px-2 rounded"

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
      "Hello from ff4s 👋" // Strings are valid child nodes, of course.
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

  /* If a component requires access to state, we can use `useState{ state => ...}`,
   * which is just an alias for `getState.flatMap{ state => ...}`.
   */
  val counter = useState { state =>
    div(
      cls := "m-4",
      h2(
        cls := subHeadingCls,
        "A simple counter"
      ),
      p(
        s"The counter stands at ${state.counter}. Click the buttons to increment or decrement the counter."
      ),
      div(
        cls := "flex flex-row justify-center",
        button(
          tpe := "button",
          cls := buttonCls,
          // We can assign callbacks to events; note that the callback
          // returns an `Option[Action]`, which, when defined, is dispatched
          // by our store. Returning `None` means we don't do anything.
          onClick := (_ => Some(Action.IncrementCounter)),
          plusIcon
        ),
        button(
          tpe := "button",
          cls := buttonCls,
          onClick := (_ => Some(Action.DecrementCounter)),
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
            /* We typically have to match on the type of the target to get the
             * desired information. */
            (ev: dom.Event) =>
              ev.target match {
                case el: dom.HTMLInputElement => Some(Action.SetName(el.value))
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
                Some(Action.SetFavoriteDish(Dish.fromString(el.value).get))
              case _ => None
            }
          ),
          // `Seq`s of components are also valid child elements.
          // In this case one should always define the `key` attribute
          // to something that is unique among all siblings.
          Dish.all.map(food =>
            option(
              selected := (food == state.favoriteDish),
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
      div(span(cls := "text-xl", "✨You found the magic combination✨"))
    else empty // The `empty` component is convenient for conditional rendering
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
              onChange := (_ => Some(Action.SetPets(pets)))
            ),
            pets.toString
          )
        }
      ),
      span(
        {
          state.pets match {
            case Pets.Cats => "Meow!"
            case Pets.Dogs => "Woof!"
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
        import dsl.svg._
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
    div(
      cls := "m-1 flex flex-col items-center",
      h2(cls := subHeadingCls, "A WebSocket example"),
      p("Echo Server"),
      div(
        cls := "flex flex-col",
        input(
          tpe := "text",
          cls := "text-center m-1 rounded font-light shadow",
          placeholder := "type something here...",
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                Some(Action.SendWebsocketMessage(el.value))
              case _ => None
            }
          )
        ),
        span(
          cls := "text-center",
          s"Websocket Response:  ${state.websocketResponse.getOrElse("")}"
        )
      )
    )
  }

  val idExample =
    useId { uid =>
      div(
        cls := "m-1 flex flex-col items-center",
        h2(cls := subHeadingCls, "Unique IDs"),
        div(s"Some id: ${uid}"),
        useId { uid =>
          div(s"Another id: $uid")
        },
        useId { uid =>
          div(s"Yet another id: $uid")
        }
      )
    }

  val uuidExample = useUUID { uid =>
    div(
      cls := "m-1 flex flex-col items-center",
      h2(cls := subHeadingCls, "A random UUID"),
      div(uid.toString)
    )
  }

  val root = div(
    cls := "p-4 flex flex-col items-center bg-no-repeat h-full bg-gradient-to-tr from-gray-200 to-sky-300 text-gray-800 font-light",
    welcome,
    magicAlert,
    intro,
    counter,
    apiCalls,
    textInput,
    dropDown,
    radioButtons,
    svgDemo,
    websocketExample,
    idExample,
    uuidExample
  )

}
