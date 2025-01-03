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

import cats.effect.Concurrent
import org.scalajs.dom

class App[F[_]](implicit val F: Concurrent[F])
    extends ff4s.App[F, State, Action] {

  override val store = ff4s.Store.pure[F, State, Action](State()) {
    case (Action.SetFilter(filter), state) => state.copy(filter = filter)

    case (Action.UpdateTodo(todo), state) =>
      state.copy(todos = state.todos + (todo.id -> todo))

    case (Action.AddTodo, state) => {
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

    case (Action.RemoveTodo(id), state) =>
      state.copy(todos = state.todos - id)

    case (Action.SetTodoInput(what), state) =>
      state.copy(todoInput = Some(what))
  }

  import html._

  val todoInput = useState { state =>
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

  def todoItem(todo: Todo) = li(
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
          tpe := "button",
          cls := "destroy",
          onClick := (_ => Some(Action.RemoveTodo(todo.id)))
        )
      )
    }
  )

  val statusBar = useState { state =>
    val activeCount = state.todos.filterNot(_._2.complete).size
    footerTag(
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

  override val view = useState { state =>
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
