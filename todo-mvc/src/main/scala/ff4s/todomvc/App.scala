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

package ff4s.todomvc

import org.scalajs.dom
import cats.effect.kernel.Async
import scala.collection.immutable.IntMap
import cats.effect.kernel.Resource
import ff4s.Store

class App[F[_]: Async] {

  case class State(
      todos: Map[Int, Todo] = IntMap.empty[Todo],
      nextId: Int = 0,
      todoInput: Option[String] = None,
      filter: Filter = All
  )

  case class Todo(what: String, id: Int, complete: Boolean, isEdit: Boolean)

  sealed trait Filter {
    def pred: Todo => Boolean
  }

  case object All extends Filter {
    override def pred = (_ => true)
  }
  case object Active extends Filter {
    override def pred = (todo => !todo.complete)
  }

  case object Completed extends Filter {
    override def pred = (_.complete)
  }

  object Filter {
    val values: List[Filter] = List(All, Active, Completed)
  }

  // Define a set of actions
  sealed trait Action
  case object AddTodo extends Action
  case class SetFilter(filter: Filter) extends Action
  case class UpdateTodo(todo: Todo) extends Action
  case class RemoveTodo(id: Int) extends Action
  case class SetTodoInput(what: String) extends Action

  implicit val store: Resource[F, Store[F, State, Action]] =
    ff4s.Store[F, State, Action](State()) { ref => (a: Action) =>
      a match {
        case SetFilter(filter) => ref.update(_.copy(filter = filter))
        case UpdateTodo(todo) =>
          ref.update { state =>
            state.copy(todos = state.todos + (todo.id -> todo))
          }
        case AddTodo =>
          ref.update { state =>
            val nextId = state.nextId
            state.todoInput match {
              case Some(what) if what.nonEmpty =>
                state.copy(
                  nextId = nextId + 1,
                  todos =
                    state.todos + (nextId -> Todo(what, nextId, false, false)),
                  todoInput = None
                )
              case _ => state
            }
          }
        case RemoveTodo(id) =>
          ref.update { state => state.copy(todos = state.todos - id) }
        case SetTodoInput(what) => ref.update(_.copy(todoInput = Some(what)))
      }
    }

  val dsl = new ff4s.Dsl[F, State, Action]

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
          case el: dom.HTMLInputElement => Some(SetTodoInput(el.value))
          case _                        => None
        }
      ),
      onKeyDown := ((ev: dom.KeyboardEvent) => {
        ev.key match {
          case "Enter" => Some(AddTodo)
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
    onDblClick := (_ => Some(UpdateTodo(todo.copy(isEdit = true)))),
    if (todo.isEdit) {
      List(
        input(
          cls := "edit",
          defaultValue := todo.what,
          value := todo.what,
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                Some(UpdateTodo(todo.copy(what = el.value)))
              case _ => None
            }
          ),
          onKeyDown := ((ev: dom.KeyboardEvent) => {
            ev.key match {
              case "Enter" => Some(UpdateTodo(todo.copy(isEdit = false)))
              case _       => None
            }
          }),
          onBlur := (_ => Some(UpdateTodo(todo.copy(isEdit = false))))
        )
      )
    } else {
      List(
        input(
          cls := "toggle",
          typ := "checkbox",
          checked := todo.complete,
          onInput := (_ =>
            Some(UpdateTodo(todo.copy(complete = !todo.complete)))
          )
        ),
        label(todo.what),
        button(
          cls := "destroy",
          onClick := (_ => Some(RemoveTodo(todo.id)))
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
              onClick := (_ => Some(SetFilter(f))),
              f.toString
            )
          )
        }
      )
    )
  }

  val app = useState { state =>
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

  def run: F[Nothing] = app.renderInto("#app")

}
