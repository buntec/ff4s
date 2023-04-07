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

package examples.example1

import org.scalajs.dom
import cats.effect.Concurrent

// The obligatory to-do list app.
class App[F[_]: Concurrent] extends ff4s.App[F, State, Action] {

  val store = Store[F]

  import dsl._ // basic dsl
  import dsl.syntax.html._ // nice syntax for html tags, attributes etc.

  val heading =
    h1( // All common html tags are available thanks to scala-dom-types.
      cls := "m-4 text-4xl", // Some tailwindcss utility classes.
      "A To-Do List App" // Strings are valid child nodes, of course.
    )

  val todoInput = useState { state =>
    div(
      cls := "flex flex-row",
      label(
        cls := "m-1 p-1",
        span(cls := "m-1", "What?"),
        input(
          cls := "m-1 rounded text-center",
          tpe := "text",
          value := state.todoInput.getOrElse(""),
          onKeyUp := ((ev: dom.KeyboardEvent) =>
            ev.key match {
              case "Enter" => Some(Action.AddTodo)
              case _       => None
            }
          ),
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                Some(Action.SetTodoInput(el.value))
              case _ => None
            }
          )
        ),
        button(
          "Add",
          cls := "mx-1 px-4 py-1 shadow bg-emerald-500 text-zinc-200 hover:bg-emerald-600 active:bg-emerald-700 rounded",
          onClick := (_ => Some(Action.AddTodo))
        )
      )
    )
  }

  // We can use (unsafe) literals when it is convenient, e.g., for SVG icons.
  val deleteIcon = literal("""<svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
</svg>""")

  val todoList = useState { state =>
    ol(
      // `Seq`s are valid child nodes - should use unique `key`
      state.todos.map(todo =>
        li(
          key := todo.id,
          cls := "m-1",
          div(
            cls := "flex flex-row justify-between rounded border border-gray-400",
            div(
              cls := "w-full flex flex-row justify-between",
              span(cls := "m-1 px-4 text-left", todo.what),
              span(cls := "m-1", s"#${todo.id}")
            ),
            button(
              tpe := "button",
              cls := "m-1 rounded text-red-500 hover:text-red-600",
              deleteIcon,
              onClick := (_ => Some(Action.RemoveTodo(todo.id)))
            )
          )
        )
      )
    )
  }

  val root = div(
    cls := "p-4 flex flex-col h-screen items-center bg-gray-200 text-gray-800 font-light",
    heading,
    todoInput,
    todoList
  )

}
