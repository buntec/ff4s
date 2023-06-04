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

import cats.Show
import cats.syntax.all._
import org.scalajs.dom

/* A set of reusable components.
 *
 * Note that everything is polymorphic in the State and Action types S and A.
 * Due to how dependent types work, we cannot pass the dsl into the class
 * constructor and instead pass it to every method. For convenience, we do this
 * implicitly where possible. */
class Components[F[_], S, A] {

  def hello(implicit dsl: ff4s.Dsl[F, S, A]): dsl.V = {
    import dsl._
    import dsl.html._

    div("hello")
  }

  /* A simple button. */
  def btn(
      label0: String,
      onClick0: S => Option[A],
      isDisabled: S => Boolean
  )(implicit dsl: ff4s.Dsl[F, S, A]): dsl.V = {
    import dsl._
    import dsl.html._

    useState { state =>
      button(
        cls := s"px-3 py-1 text-center shadow rounded bg-pink-400 hover:bg-pink-300 active:bg-pink-500 disabled:bg-gray-400",
        disabled := isDisabled(state),
        onClick := (_ => onClick0(state)),
        label0
      )
    }
  }

  /* Note that if we want to pass in child components, then the dsl cannot be
   * passed implicitly due to type dependence. */
  def fancyWrapper(
      dsl: ff4s.Dsl[F, S, A]
  )(description: String)(children: dsl.V*): dsl.V = {
    import dsl._
    import dsl.html._

    div(
      cls := "m-1 p-1 border border-purple-500 rounded flex flex-col items-center",
      description,
      children
    )
  }

  def pageWithHeaderAndFooter(
      dsl: ff4s.Dsl[F, S, A]
  )(title0: String)(children: dsl.V*): dsl.V = {
    import dsl._
    import dsl.html._

    div(
      cls := "bg-zinc-100 text-zinc-900 w-full h-screen flex flex-col justify-between font-light",
      div(
        cls := "h-10 bg-zinc-300 text-indigo-500 text-lg flex flex-row items-center justify-center shadow",
        title0
      ),
      children,
      div(cls := "h-10 bg-zinc-300 shadow")
    )
  }

  /* A counter that can be incremented and decremented. */
  def counter(count: S => Int, inc: S => A, dec: S => A)(implicit
      dsl: ff4s.Dsl[F, S, A]
  ): dsl.V = {
    import dsl._
    import dsl.html._

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

  /* A drop-down select with label. */
  def labeledSelect[O: Show](
      label0: String,
      fromString: String => Option[O],
      onChange0: (S, O) => Option[A],
      options: List[O],
      selected0: S => O
  )(implicit dsl: ff4s.Dsl[F, S, A]): dsl.V = {
    import dsl._
    import dsl.html._

    useState { state =>
      div(
        cls := "font-light flex flex-row items-center p-1 gap-1 border border-zinc-400 rounded",
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
