package examples.example3

import ff4s.VNode
import org.scalajs.dom
import cats.Show
import cats.syntax.all._

// S = State, A = Action
class Components[F[_], S, A] {

  // no state, no actions
  def hello(implicit dsl: ff4s.Dsl[F, S, A]): dsl.View[VNode[F]] = {

    import dsl.syntax.html._

    div("hello")
  }

  // a simple button
  def btn(
      label0: String,
      onClikk: S => Option[A],
      isDisabled: S => Boolean
  )(implicit
      dsl: ff4s.Dsl[F, S, A]
  ): dsl.View[VNode[F]] = {

    import dsl._
    import dsl.syntax.html._

    useState { state =>
      button(
        cls := s"px-3 py-1 text-center shadow rounded bg-pink-400 hover:bg-pink-300 active:bg-pink-500 disabled:bg-gray-400",
        disabled := isDisabled(state),
        onClick := (_ => onClikk(state)),
        label0
      )
    }
  }

  // state but no actions
  def counter(count: S => Int, inc: S => A, dec: S => A)(implicit
      dsl: ff4s.Dsl[F, S, A]
  ): dsl.View[VNode[F]] = {

    import dsl._
    import dsl.syntax.html._

    useState { state =>
      div(
        cls := "flex flex-col items-center gap-2 p-2",
        div(cls := "p-1 bg-teal-400 rounded", s"counter=${count(state)}"),
        div(
          cls := "flex flex-row items-center justify-center gap-2",
          btn("+", s => inc(s).some, _ => false),
          btn("-", s => dec(s).some, _ => false)
        )
      )
    }
  }

  // a drop-down with label
  def labeledSelect[V: Show](
      label0: String,
      fromString: String => Option[V],
      onChange0: (S, V) => Option[A],
      options: List[V],
      selected0: S => V
  )(implicit
      dsl: ff4s.Dsl[F, S, A]
  ): dsl.View[VNode[F]] = {

    import dsl._
    import dsl.syntax.html._

    useState { state =>
      div(
        cls := "font-light flex flex-row items-center p-2 gap-1 border border-zinc-400 rounded uppercase",
        label(span(label0)),
        select(
          cls := "m-1 rounded appearance-none",
          onChange := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLSelectElement =>
                fromString(el.value).flatMap(onChange0(state, _))
              case _ => None
            }
          ),
          options.map { name =>
            option(
              cls := "p-2",
              selected := (name == selected0(state)),
              key := name.show,
              value := name.show,
              name.show
            )
          }
        )
      )
    }
  }

}
