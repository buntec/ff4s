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

package ff4s

import cats.effect.IO
import cats.effect.Resource
import cats.effect.kernel.Outcome
import cats.syntax.all._
import munit.CatsEffectSuite
import org.scalajs.dom

import scala.scalajs.js

import TestUtils._

class RenderSuite extends CatsEffectSuite {

  // jsdom only provides requestAnimationFrame in "visual" mode
  override def beforeAll(): Unit = {
    val window = js.Dynamic.global.window
    if (js.isUndefined(window.requestAnimationFrame)) {
      window.requestAnimationFrame = { (cb: js.Function1[Double, Unit]) =>
        js.timers.setTimeout(0)(cb(0.0)): Unit
        0
      }: js.Function1[js.Function1[Double, Unit], Int]
    }
  }

  case class State(
      count: Int = 0,
      inserted: Boolean = false,
      destroyed: Boolean = false,
      log: Vector[Action] = Vector.empty // every action the store received
  )

  sealed trait Action
  case object Inc extends Action
  case object Inserted extends Action
  case object Destroyed extends Action

  private val dsl = Dsl[State, Action]
  import dsl._
  import dsl.html._

  private val mkStore = Store.pure[IO, State, Action](State()) { (a, s0) =>
    val s = s0.copy(log = s0.log :+ a)
    a match {
      case Inc       => s.copy(count = s.count + 1)
      case Inserted  => s.copy(inserted = true)
      case Destroyed => s.copy(destroyed = true)
    }
  }

  private var counter = 0

  /** Renders `view` into a fresh container and runs `body` on that container.
    *
    * Rendering never terminates on its own, so if it stops before `body`
    * finishes, the test fails at once with the underlying error instead of
    * waiting for an assertion to time out.
    */
  private def withRender[A](
      view: V,
      store: Resource[IO, Store[IO, State, Action]] = mkStore,
      replaceRoot: Boolean = true
  )(body: dom.Element => IO[A]): IO[A] =
    Resource
      .make(IO {
        counter += 1
        val container = dom.document.createElement("div")
        val root = dom.document.createElement("div")
        root.id = s"ff4s-root-$counter"
        container.appendChild(root)
        dom.document.body.appendChild(container)
        container
      })(c => IO(c.remove()))
      .flatMap { container =>
        val rootId = container.firstElementChild.id
        implicit val implicitStore: Resource[IO, Store[IO, State, Action]] =
          store
        val rendering =
          if (replaceRoot) view.renderReplace[IO](rootId)
          else view.renderInto[IO](rootId)
        rendering.background.tupleLeft(container)
      }
      .use { case (container, outcome) =>
        val renderStopped = outcome.flatMap[A] {
          case Outcome.Errored(e) => IO.raiseError(e)
          case other              =>
            IO.raiseError(new AssertionError(s"rendering stopped: $other"))
        }
        IO.race(renderStopped, body(container)).map(_.merge)
      }

  private def htmlOf(container: dom.Element) = IO(container.innerHTML)

  private def renders(container: dom.Element, html: String) =
    waitUntil(htmlOf(container).map(_ == html))
      .handleErrorWith(_ =>
        htmlOf(container).flatMap(actual => IO(assertEquals(actual, html)))
      )

  test("renders a static view") {
    val view = div(cls := "box", span("hello"), text(" world"))
    withRender(view)(
      renders(_, """<div class="box"><span>hello</span> world</div>""")
    )
  }

  test("re-renders when an event handler dispatches an action") {
    val view = useState { s =>
      button(onClick := (_ => Some(Inc)), s"count: ${s.count}")
    }
    withRender(view) { c =>
      def click =
        IO(c.querySelector("button").asInstanceOf[dom.HTMLElement].click())
      renders(c, "<button>count: 0</button>") *>
        click *>
        renders(c, "<button>count: 1</button>") *>
        click *> click *>
        renders(c, "<button>count: 3</button>")
    }
  }

  test("event handlers returning None do not dispatch") {
    val view = useState { s =>
      div(
        button(cls := "none", onClick := (_ => None)),
        button(cls := "inc", onClick := (_ => Some(Inc))),
        span(s.log.mkString(","))
      )
    }
    def click(c: dom.Element, sel: String) =
      c.querySelector(sel).asInstanceOf[dom.HTMLElement].click()
    withRender(view) { c =>
      // Actions are dispatched in click order, so once `Inc` (the marker) is
      // in the log, anything dispatched by the first click would be too.
      renders(
        c,
        """<div><button class="none"></button><button class="inc"></button><span></span></div>"""
      ) *>
        IO { click(c, ".none"); click(c, ".inc") } *>
        renders(
          c,
          """<div><button class="none"></button><button class="inc"></button><span>Inc</span></div>"""
        )
    }
  }

  test("insert hooks dispatch actions") {
    val view = useState { s =>
      div(insertHook := (_ => Inserted), if (s.inserted) "yes" else "no")
    }
    withRender(view)(renders(_, "<div>yes</div>"))
  }

  test("boolean attributes, props and styles") {
    val view = div(
      button(disabled := true, "a"),
      button(disabled := false, "b"),
      span(styleAttr := "color: red;"),
      input(typ := "checkbox", checked := true),
      p(Modifier.Style("color", "blue"))
    )
    withRender(view) { c =>
      renders(
        c,
        """<div><button disabled="">a</button><button>b</button><span style="color: red;"></span><input type="checkbox"><p style="color: blue;"></p></div>"""
      ) *> IO {
        // `checked` is a DOM property, not an attribute
        assert(
          c.querySelector("input").asInstanceOf[dom.HTMLInputElement].checked
        )
        assertEquals(
          c.querySelector("p").asInstanceOf[dom.HTMLElement].style.color,
          "blue"
        )
      }
    }
  }

  test("withClass overrides the class attribute") {
    val view = div(cls := "a").withClass("b")
    withRender(view)(renders(_, """<div class="b"></div>"""))
  }

  test("literal renders raw HTML") {
    val view = div(literal("""<svg><circle r="1"></circle></svg>"""))
    withRender(view)(
      renders(_, """<div><svg><circle r="1"></circle></svg></div>""")
    )
  }

  test("getId yields 1, 2, ... and is stable across renders") {
    val view = useId { a =>
      useId { b =>
        useState(s => button(onClick := (_ => Some(Inc)), s"$a-$b-${s.count}"))
      }
    }
    withRender(view) { c =>
      renders(c, "<button>1-2-0</button>") *>
        IO(c.querySelector("button").asInstanceOf[dom.HTMLElement].click()) *>
        renders(c, "<button>1-2-1</button>")
    }
  }

  test("destroy hooks dispatch actions when an element is removed") {
    val view = useState { s =>
      div(
        button(onClick := (_ => Some(Inc))),
        if (s.count == 0) span(destroyHook := (_ => Destroyed)) else empty,
        if (s.destroyed) "destroyed" else ""
      )
    }
    withRender(view) { c =>
      renders(c, "<div><button></button><span></span></div>") *>
        IO(c.querySelector("button").asInstanceOf[dom.HTMLElement].click()) *>
        renders(c, "<div><button></button>destroyed</div>")
    }
  }

  test("renderInto renders inside the root element instead of replacing it") {
    val view = span("hi")
    withRender(view, replaceRoot = false) { c =>
      val rootId = c.firstElementChild.id
      renders(c, s"""<div id="$rootId"><span>hi</span></div>""")
    }
  }

  test("errors while rendering surface immediately") {
    val view = useState[VNode[Action]](_ => throw new Exception("boom"))
    withRender(view)(_ => IO.never[Unit]).attempt.map { result =>
      assertEquals(result.left.map(_.getMessage), Left("boom"))
    }
  }

  test("void elements cannot have children") {
    intercept[IllegalArgumentException](input(span("x")))
  }

}
