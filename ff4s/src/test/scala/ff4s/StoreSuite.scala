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
import cats.syntax.all._
import munit.CatsEffectSuite

import scala.concurrent.duration._

import TestUtils._

class StoreSuite extends CatsEffectSuite {

  sealed trait Action
  case class Add(n: Int) extends Action
  case object Reset extends Action
  case class AddThenDouble(n: Int) extends Action
  case object Double extends Action

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
          case (Reset, _)            => (0, IO.unit)
          case (Double, s)           => (s * 2, IO.unit)
          case (AddThenDouble(n), s) =>
            (
              s + n,
              store.state.get.flatMap(s => seen.set(Some(s))) *>
                store.dispatch(Double)
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

  test("withCancellationKey cancels the previous evaluation") {
    Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
      for {
        firstCanceled <- Deferred[IO, Unit]
        first <- store
          .withCancellationKey("k")(
            IO.never[Unit].onCancel(firstCanceled.complete(()).void)
          )
          .start
        // wait until the first effect is registered under the key
        _ <- IO.sleep(20.millis)
        _ <- store.withCancellationKey("k")(IO.unit)
        _ <- firstCanceled.get.timeout(1.second)
        outcome <- first.join
      } yield assert(outcome.isCanceled || outcome.isSuccess)
    }
  }

  test("cancel(key) cancels a running effect") {
    Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
      for {
        canceled <- Deferred[IO, Unit]
        _ <- store
          .withCancellationKey("k")(
            IO.never[Unit].onCancel(canceled.complete(()).void)
          )
          .start
        _ <- IO.sleep(20.millis)
        _ <- store.cancel("k")
        _ <- canceled.get.timeout(1.second)
      } yield ()
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
        g1 <- Deferred[IO, Unit]
        g2 <- Deferred[IO, Unit]
        f1 <- store.withRunningState("load")(g1.get).start
        f2 <- store.withRunningState("load")(g2.get).start
        _ <- waitUntil(running.get)
        _ <- g1.complete(()) *> f1.joinWithNever
        _ <- assertIO(running.get, true)
        _ <- g2.complete(()) *> f2.joinWithNever
        _ <- assertIO(running.get, false)
      } yield ()
    }
  }

  test("runningState resets when the effect fails") {
    Store.pure[IO, Unit, Unit](())((_, s) => s).use { store =>
      store
        .withRunningState("load")(IO.raiseError(new Exception("boom")))
        .attempt *> assertIO(store.runningState("load").get, false)
    }
  }

}
