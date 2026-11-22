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

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.testkit.TestControl
import cats.syntax.all._
import munit.CatsEffectSuite

import scala.concurrent.duration._

import TestUtils._

class StoreSuite extends CatsEffectSuite {

  sealed trait Action
  case class Add(n: Int) extends Action
  case class AddThenDouble(n: Int) extends Action
  case object Times2 extends Action

  test("pure store applies dispatched actions in order") {
    Store
      .pure[IO, List[Int], Int](Nil)((n, s) => s :+ n)
      .use { store =>
        (1 to 100).toList.traverse_(store.dispatch) *>
          waitUntil(store.state.get.map(_.size == 100)) *>
          assertIO(store.state.get, (1 to 100).toList)
      }
  }

  test("initial state is available immediately") {
    Store.pure[IO, Int, Action](42)((_, s) => s).use { store =>
      assertIO(store.state.get, 42)
    }
  }

  test("side effects run after the state update and may dispatch actions") {
    Ref[IO].of(Option.empty[Int]).flatMap { seen =>
      Store[IO, Int, Action](0) { store =>
        {
          case (Add(n), s)           => (s + n, IO.unit)
          case (Times2, s)           => (s * 2, IO.unit)
          case (AddThenDouble(n), s) =>
            (
              s + n,
              store.state.get.flatMap(s => seen.set(Some(s))) *>
                store.dispatch(Times2)
            )
        }
      }.use { store =>
        store.dispatch(Add(1)) *>
          store.dispatch(AddThenDouble(2)) *>
          waitUntil(store.state.get.map(_ == 6)) *>
          assertIO(seen.get, Some(3)) // effect observed the updated state
      }
    }
  }

  // These tests run under `TestControl`, where virtual time only advances once
  // every fiber is blocked. So after `IO.sleep`, the first wrapper is known to
  // have supervised its effect *and* registered the fiber under the key.

  test("withCancellationKey cancels the previous evaluation") {
    TestControl.executeEmbed {
      Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
        for {
          firstCanceled <- Deferred[IO, Unit]
          first <- store
            .withCancellationKey("k")(
              IO.never[Unit].onCancel(firstCanceled.complete(()).void)
            )
            .start
          _ <- IO.sleep(1.milli)
          _ <- store.withCancellationKey("k")(IO.unit)
          _ <- firstCanceled.get.timeout(1.second)
          outcome <- first.join
          // the superseded wrapper completes normally rather than being canceled
        } yield assert(outcome.isSuccess, outcome)
      }
    }
  }

  test("cancel(key) cancels a running effect") {
    TestControl.executeEmbed {
      Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
        for {
          canceled <- Deferred[IO, Unit]
          _ <- store
            .withCancellationKey("k")(
              IO.never[Unit].onCancel(canceled.complete(()).void)
            )
            .start
          _ <- IO.sleep(1.milli)
          _ <- store.cancel("k")
          _ <- canceled.get.timeout(1.second)
        } yield ()
      }
    }
  }

  test(
    "canceling the wrapper returned by withCancellationKey cancels the effect"
  ) {
    TestControl.executeEmbed {
      Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
        for {
          canceled <- Deferred[IO, Unit]
          outer <- store
            .withCancellationKey("k")(
              IO.never[Unit].onCancel(canceled.complete(()).void)
            )
            .start
          _ <- IO.sleep(1.milli)
          _ <- outer.cancel
          _ <- canceled.get.timeout(1.second)
        } yield ()
      }
    }
  }

  test("runningState resets when the effect is canceled") {
    TestControl.executeEmbed {
      Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
        val running = store.runningState("load")
        for {
          fiber <- store.withRunningState("load")(IO.never[Unit]).start
          _ <- IO.sleep(1.milli)
          _ <- assertIO(running.get, true)
          _ <- fiber.cancel
          _ <- assertIO(running.get, false)
        } yield ()
      }
    }
  }

  // `withRunningState(...)(withCancellationKey(...)(fu))` and the reverse
  // nesting are documented to behave the same: the running state lasts exactly
  // as long as `fu`, and `cancel(key)` ends both.
  List[(String, Store[IO, Unit, Unit] => IO[Unit] => IO[Unit])](
    "withRunningState outside" -> (store =>
      fu => store.withRunningState("r")(store.withCancellationKey("k")(fu))
    ),
    "withCancellationKey outside" -> (store =>
      fu => store.withCancellationKey("k")(store.withRunningState("r")(fu))
    )
  ).foreach { case (name, wrap) =>
    test(s"withRunningState composes with withCancellationKey ($name)") {
      TestControl.executeEmbed {
        Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
          val running = store.runningState("r")
          for {
            canceled <- Deferred[IO, Unit]
            outer <- wrap(store)(
              IO.never[Unit].onCancel(canceled.complete(()).void)
            ).start
            _ <- IO.sleep(1.milli)
            _ <- assertIO(running.get, true)
            _ <- store.cancel("k")
            _ <- canceled.get.timeout(1.second)
            outcome <- outer.join.timeout(1.second)
            _ = assert(outcome.isSuccess, outcome)
            _ <- assertIO(running.get, false)
          } yield ()
        }
      }
    }
  }

  test("cancel(key) on an unknown key is a no-op") {
    Store.pure[IO, Unit, Unit](())((_, s) => s).use(_.cancel("nope"))
  }

  test("runningState is true exactly while the effect runs") {
    Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
      val running = store.runningState("load")
      for {
        _ <- assertIO(running.get, false)
        gate <- Deferred[IO, Unit]
        fiber <- store.withRunningState("load")(gate.get).start
        _ <- waitUntil(running.get)
        _ <- gate.complete(())
        _ <- fiber.joinWithNever
        _ <- assertIO(running.get, false)
      } yield ()
    }
  }

  test("runningState stays true while any of several effects runs") {
    Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
      val running = store.runningState("load")
      for {
        entered1 <- Deferred[IO, Unit]
        entered2 <- Deferred[IO, Unit]
        g1 <- Deferred[IO, Unit]
        g2 <- Deferred[IO, Unit]
        f1 <- store
          .withRunningState("load")(entered1.complete(()) *> g1.get)
          .start
        f2 <- store
          .withRunningState("load")(entered2.complete(()) *> g2.get)
          .start
        // both bodies are running, so they overlap
        _ <- entered1.get *> entered2.get
        _ <- assertIO(running.get, true)
        _ <- g1.complete(()) *> f1.joinWithNever
        _ <- assertIO(running.get, true)
        _ <- g2.complete(()) *> f2.joinWithNever
        _ <- assertIO(running.get, false)
      } yield ()
    }
  }

  test(
    "withRunningState runs a failing effect, propagates its error and resets"
  ) {
    Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
      val running = store.runningState("load")
      val boom = new Exception("boom")
      for {
        runningInside <- Ref[IO].of(false)
        result <- store
          .withRunningState("load")(
            running.get.flatMap(runningInside.set) *> IO.raiseError[Unit](boom)
          )
          .attempt
        _ = assertEquals(result, Left(boom))
        _ <- assertIO(runningInside.get, true)
        _ <- assertIO(running.get, false)
      } yield ()
    }
  }

}
