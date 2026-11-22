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
import cats.syntax.all._
import munit.CatsEffectSuite
import org.scalajs.dom

import scala.concurrent.duration._
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

  case class State(count: Int = 0, inserted: Boolean = false)

  sealed trait Action
  case object Inc extends Action
  case object Inserted extends Action

  private val dsl = Dsl[State, Action]
  import dsl._
  import dsl.html._

  private val mkStore = Store.pure[IO, State, Action](State()) {
    case (Inc, s)      => s.copy(count = s.count + 1)
    case (Inserted, s) => s.copy(inserted = true)
  }

  private var counter = 0

  /** Renders `view` into a fresh container and yields that container. */
  private def render(
      view: V,
      store: Resource[IO, Store[IO, State, Action]] = mkStore
  ): Resource[IO, dom.Element] =
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
      .flatTap { container =>
        Render(dsl, store)(
          view,
          container.firstElementChild.id,
          replaceRoot = true
        ).background
      }

  private def htmlOf(container: dom.Element) = IO(container.innerHTML)

  private def renders(container: dom.Element, html: String) =
    waitUntil(htmlOf(container).map(_ == html))
      .handleErrorWith(_ =>
        htmlOf(container).flatMap(actual => IO(assertEquals(actual, html)))
      )

  test("renders a static view") {
    val view = div(cls := "box", span("hello"), text(" world"))
    render(view).use(
      renders(_, """<div class="box"><span>hello</span> world</div>""")
    )
  }

  test("re-renders when an event handler dispatches an action") {
    val view = useState { s =>
      button(onClick := (_ => Some(Inc)), s"count: ${s.count}")
    }
    render(view).use { c =>
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
    val view = useState(s => button(onClick := (_ => None), s.count.toString))
    render(view).use { c =>
      renders(c, "<button>0</button>") *>
        IO(c.querySelector("button").asInstanceOf[dom.HTMLElement].click()) *>
        IO.sleep(50.millis) *>
        renders(c, "<button>0</button>")
    }
  }

  test("insert hooks dispatch actions") {
    val view = useState { s =>
      div(insertHook := (_ => Inserted), if (s.inserted) "yes" else "no")
    }
    render(view).use(renders(_, "<div>yes</div>"))
  }

  test("boolean attributes, props and styles") {
    val view = div(
      button(disabled := true, "a"),
      button(disabled := false, "b"),
      span(styleAttr := "color: red;")
    )
    render(view).use(
      renders(
        _,
        """<div><button disabled="">a</button><button>b</button><span style="color: red;"></span></div>"""
      )
    )
  }

  test("withClass overrides the class attribute") {
    val view = div(cls := "a").withClass("b")
    render(view).use(renders(_, """<div class="b"></div>"""))
  }

  test("literal renders raw HTML") {
    val view = div(literal("""<svg><circle r="1"></circle></svg>"""))
    render(view).use(
      renders(_, """<div><svg><circle r="1"></circle></svg></div>""")
    )
  }

  test("getId yields 1, 2, ... and is stable across renders") {
    val view = useId { a =>
      useId { b =>
        useState(s => button(onClick := (_ => Some(Inc)), s"$a-$b-${s.count}"))
      }
    }
    render(view).use { c =>
      renders(c, "<button>1-2-0</button>") *>
        IO(c.querySelector("button").asInstanceOf[dom.HTMLElement].click()) *>
        renders(c, "<button>1-2-1</button>")
    }
  }

  test("void elements cannot have children") {
    intercept[IllegalArgumentException](input(span("x")))
  }

}
