/* Copyright 2022 buntec
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */

package examples.example2

import scala.concurrent.duration._

import cats.effect.implicits._
import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Queue
import cats.effect.std.Random
import cats.syntax.all._
import fs2.Stream
import io.circe.generic.auto._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] = for {

    wsSendQ <- Queue.bounded[F, String](100).toResource

    store <- ff4s.Store[F, State, Action](State()) { ref =>
      _ match {
        case Action.WebsocketMessageReceived(msg) =>
          ref.update(state => state.copy(websocketResponse = Some(msg)))
        case Action.SendWebsocketMessage(msg) => wsSendQ.offer(msg)
        case Action.SetSvgCoords(x, y) =>
          ref.update(_.copy(svgCoords = SvgCoords(x, y)))
        case Action.Magic => ref.update(_.copy(magic = true))
        case Action.SetName(name) =>
          ref.update(
            _.copy(name = if (name.nonEmpty) Some(name) else None)
          )
        case Action.SetPets(pets) =>
          ref.update(_.copy(pets = pets))
        case Action.SetFavoriteDish(dish) =>
          ref.update(_.copy(favoriteDish = dish))
        case Action.IncrementCounter =>
          ref.update(s => s.copy(counter = s.counter + 1))
        case Action.DecrementCounter =>
          ref.update(s => s.copy(counter = s.counter - 1))
        case Action.GetActivity =>
          ff4s
            .HttpClient[F]
            .get[Bored]("http://www.boredapi.com/api/activity")
            .flatMap { bored =>
              ref.update(s => s.copy(bored = Some(bored)))
            }
      }
    }

    _ <- ff4s
      .WebSocketsClient[F]
      .bidirectionalText(
        "wss://ws.postman-echo.com/raw/",
        is =>
          is.evalMap { msg =>
            store.dispatch(Action.WebsocketMessageReceived(msg))
          },
        Stream.fromQueueUnterminated(wsSendQ)
      )
      .background

    // We can do something fancy in the background.
    _ <- Async[F].background(
      (fs2.Stream.emit(()) ++ fs2.Stream.fixedDelay(5.second))
        .covary[F]
        .evalMap(_ => store.dispatch(Action.GetActivity))
        .compile
        .drain
    )
    // We can also listen to and react to state changes.
    _ <- Async[F].background(
      store.state.discrete
        .map { state =>
          /* Use `toString` here to avoid having to provide a Eq typeclass
           * instance. */
          (state.favoriteDish.toString, state.counter)
        }
        .changes
        .filter(p => p._1 == Ramen.toString && p._2 == 3)
        .evalMap(_ => store.dispatch(Action.Magic))
        .compile
        .drain
    )
    // Animate our SVG with some random numbers.
    rng <- Resource.eval(Random.scalaUtilRandom)
    _ <- Async[F].background(
      fs2.Stream
        .fixedDelay(1.second)
        .evalMap { _ =>
          for {
            x <- rng.betweenDouble(10.0, 90.0)
            y <- rng.betweenDouble(10.0, 90.0)
          } yield (x, y)
        }
        .evalMap { case (x, y) => store.dispatch(Action.SetSvgCoords(x, y)) }
        .compile
        .drain
    )
  } yield store

}
