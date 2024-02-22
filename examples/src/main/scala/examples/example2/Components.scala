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

import cats.Eq
import cats.Show
import cats.syntax.all._
import org.scalajs.dom

/* We recommend organizing reusable components into traits
 * with self-type `ff4s.Dsl` and polymorphic State and Action types.
 */
trait Components[S, A] { dsl: ff4s.Dsl[S, A] =>

  import html._

  val hello: V = div("hello")

  /* A simple button. */
  def btn(
      label: String,
      onClick: Option[A],
      isDisabled: Boolean
  ): V =
    button(
      cls := s"px-3 py-1 text-center shadow rounded bg-pink-400 hover:bg-pink-300 active:bg-pink-500 disabled:bg-gray-400",
      disabled := isDisabled,
      html.onClick := (_ => onClick),
      label
    )

  def fancyWrapper(description: String)(children: V*): V =
    div(
      cls := "m-1 p-1 border border-purple-500 rounded flex flex-col items-center",
      description,
      children
    )

  def pageWithHeaderAndFooter(title0: String)(children: V*): V =
    div(
      cls := "bg-zinc-100 text-zinc-900 w-full h-screen flex flex-col justify-between font-light",
      div(
        cls := "h-10 bg-zinc-300 text-indigo-500 text-lg flex flex-row items-center justify-center shadow",
        title0
      ),
      children,
      div(cls := "h-10 bg-zinc-300 shadow")
    )

  /* A counter that can be incremented and decremented. */
  def counter(count: S => Int, inc: A, dec: A): V =
    useState { state =>
      div(
        cls := "flex flex-col items-center gap-2 p-2",
        div(cls := "p-1 bg-teal-400 rounded", s"counter=${count(state)}"),
        div(
          cls := "flex flex-row items-center justify-center gap-2",
          btn("+", inc.some, false),
          btn("-", dec.some, false)
        )
      )
    }

  /* A drop-down select with label. */
  def labeledSelect[O: Eq: Show](
      label: String,
      options: List[O],
      selected: O,
      onChange: O => Option[A]
  ): V = div(
    cls := "font-light flex flex-row items-center p-1 gap-1 border border-zinc-400 rounded",
    html.label(span(label)),
    select(
      cls := "m-1 rounded appearance-none",
      html.onChange := ((ev: dom.Event) =>
        ev.target match {
          case el: dom.HTMLSelectElement =>
            options
              .find(o => Show[O].show(o) == el.value)
              .flatMap(onChange(_))
          case _ => None
        }
      ),
      options.map { name =>
        option(
          cls := "p-2",
          html.selected := (name === selected),
          key := name.show,
          value := name.show,
          name.show
        )
      }
    )
  )

}
