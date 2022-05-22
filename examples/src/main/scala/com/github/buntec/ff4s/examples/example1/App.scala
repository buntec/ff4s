package ff4s.examples.example1

import org.scalajs.dom
import cats.effect.kernel.Async

// The obligatory to-do list app.
class App[F[_]: Async] {

  // Define our app's state space.
  case class State(
      todos: Seq[Todo] = Seq.empty,
      nextId: Int = 0,
      todoInput: Option[String] = None
  )
  case class Todo(what: String, id: Int)

  // Define a set of actions
  sealed trait Action
  case object AddTodo extends Action
  case class RemoveTodo(id: Int) extends Action
  case class SetTodoInput(what: String) extends Action

  // Build our store by assigning actions to effects.
  implicit val store = ff4s.Store[F, State, Action](State()) {
    ref => (a: Action) =>
      a match {
        case AddTodo =>
          ref.update { state =>
            val nextId = state.nextId
            state.todoInput match {
              case Some(what) if what.nonEmpty =>
                state.copy(
                  nextId = nextId + 1,
                  todos = state.todos :+ Todo(what, nextId),
                  todoInput = None
                )
              case _ => state
            }
          }
        case RemoveTodo(id) =>
          ref.update { state =>
            state.copy(todos = state.todos.filterNot(_.id == id))
          }
        case SetTodoInput(what) => ref.update(_.copy(todoInput = Some(what)))
      }
  }

  // Create the DSL for our model.
  val dsl = new ff4s.Dsl[F, State, Action]

  import dsl._ // basic dsl
  import dsl.syntax.html._ // nice syntax for html tags, attributes etc.

  val heading =
    h1( // All common html tags are available thanks to scala-dom-types.
      cls := "m-4 text-4xl", // Some tailwindcss utility classes.
      "A To-Do App" // Strings are valid child nodes, of course.
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
              case "Enter" => Some(AddTodo)
              case _       => None
            }
          ),
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement => Some(SetTodoInput(el.value))
              case _                        => None
            }
          )
        ),
        button(
          "Add",
          cls := "mx-1 px-2 shadow bg-emerald-500 text-zinc-200 hover:bg-emerald-600 active:bg-emerald-700 rounded",
          onClick := (_ => Some(AddTodo))
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
            cls := "flex flex-row justify-between",
            div(
              cls := "w-full flex flex-row justify-between",
              span(cls := "m-1 px-4 text-left", todo.what),
              span(cls := "m-1", s"#${todo.id}")
            ),
            button(
              tpe := "button",
              cls := "m-1 rounded text-red-500 hover:text-red-600",
              deleteIcon,
              onClick := (_ => Some(RemoveTodo(todo.id)))
            )
          )
        )
      )
    )
  }

  val app = div(
    cls := "mb-16 flex flex-col items-center",
    heading,
    todoInput,
    todoList
  )

  def run: F[Nothing] = app.renderInto("#app")

}
