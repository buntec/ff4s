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

package todomvc

import cats.syntax.all._
import org.scalajs.dom
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import ff4s.Store

class App[F[_]: Async] extends ff4s.App[F, State, Action] {

  val store: Resource[F, Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { ref => (a: Action) =>
      a match {
        case Action.SetFilter(filter) => ref.update(_.copy(filter = filter))
        case Action.UpdateTodo(todo) =>
          ref.update { state =>
            state.copy(todos = state.todos + (todo.id -> todo))
          }
        case Action.AddTodo =>
          ref.update { state =>
            val nextId = state.nextId
            state.todoInput match {
              case Some(what) if what.nonEmpty =>
                state.copy(
                  nextId = nextId + 1,
                  todos = state.todos + (nextId -> Todo(
                    what,
                    nextId,
                    false,
                    false
                  )),
                  todoInput = None
                )
              case _ => state
            }
          }
        case Action.RemoveTodo(id) =>
          ref.update { state => state.copy(todos = state.todos - id) }
        case Action.SetTodoInput(what) =>
          ref.update(_.copy(todoInput = Some(what)))
      }
    }

  import dsl._
  import dsl.syntax.html._

  val todoInput: View[ff4s.VNode[F]] = useState { state =>
    input(
      cls := "new-todo",
      placeholder := "What needs to be done?",
      autoFocus := true,
      value := state.todoInput.getOrElse(""),
      onInput := ((ev: dom.Event) =>
        ev.target match {
          case el: dom.HTMLInputElement => Some(Action.SetTodoInput(el.value))
          case _                        => None
        }
      ),
      onKeyDown := ((ev: dom.KeyboardEvent) => {
        ev.key match {
          case "Enter" => Some(Action.AddTodo)
          case _       => None
        }
      })
    )
  }

  def todoItem(todo: Todo): View[ff4s.VNode[F]] = li(
    cls := ((todo.complete, todo.isEdit) match {
      case (true, true)   => "completed editing"
      case (true, false)  => "completed"
      case (false, true)  => "editing"
      case (false, false) => ""
    }),
    key := todo.id.toString,
    onDblClick := (_ => Some(Action.UpdateTodo(todo.copy(isEdit = true)))),
    if (todo.isEdit) {
      List(
        input(
          cls := "edit",
          defaultValue := todo.what,
          value := todo.what,
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                Some(Action.UpdateTodo(todo.copy(what = el.value)))
              case _ => None
            }
          ),
          onKeyDown := ((ev: dom.KeyboardEvent) => {
            ev.key match {
              case "Enter" => Some(Action.UpdateTodo(todo.copy(isEdit = false)))
              case _       => None
            }
          }),
          onBlur := (_ => Some(Action.UpdateTodo(todo.copy(isEdit = false))))
        )
      )
    } else {
      List(
        input(
          cls := "toggle",
          typ := "checkbox",
          checked := todo.complete,
          onInput := (_ =>
            Some(Action.UpdateTodo(todo.copy(complete = !todo.complete)))
          )
        ),
        label(todo.what),
        button(
          cls := "destroy",
          onClick := (_ => Some(Action.RemoveTodo(todo.id)))
        )
      )
    }
  )

  val statusBar = useState { state =>
    val activeCount = state.todos.filterNot(_._2.complete).size
    footer(
      cls := "footer",
      span(
        cls := "todo-count",
        activeCount match {
          case 1 => "1 item left"
          case n => s"$n items left"
        }
      ),
      ul(
        cls := "filters",
        Filter.values.map { f =>
          li(
            a(
              cls := (if (state.filter == f) "selected" else ""),
              onClick := (_ => Some(Action.SetFilter(f))),
              f.toString
            )
          )
        }
      )
    )
  }

  val root = useState { state =>
    val filter = state.filter
    div(
      cls := "todoapp",
      div(cls := "header", h1("todos"), todoInput),
      div(
        cls := "main",
        ul(
          cls := "todo-list",
          state.todos
            .filter(kv => filter.pred(kv._2))
            .map(kv => todoItem(kv._2))
            .toSeq
        )
      ),
      if (state.todos.nonEmpty) statusBar else empty
    )
  }

}
