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

import cats.effect.Async
import cats.effect.Resource
import cats.effect.implicits._
import cats.effect.std.Queue
import cats.effect.std.Random
import cats.syntax.all._
import fs2.Stream
import io.circe.generic.auto._

import scala.concurrent.duration._

object Store {

  def apply[F[_]: Async]: Resource[F, ff4s.Store[F, State, Action]] = for {

    // send queue for the websockets connection
    wsSendQ <- Queue.bounded[F, String](100).toResource

    store <- ff4s.Store[F, State, Action](State()) {
      _ match {
        case Action.WebsocketMessageReceived(msg) =>
          _.copy(websocketResponse = msg.some) -> none

        case Action.SendWebsocketMessage(msg) =>
          _ -> (wsSendQ.offer(msg).as(none[Action])).some

        case Action.SetSvgCoords(x, y) =>
          _.copy(svgCoords = SvgCoords(x, y)) -> none

        case Action.Magic => _.copy(magic = true) -> none

        case Action.SetName(name) =>
          _.copy(name = if (name.nonEmpty) Some(name) else None) -> none

        case Action.SetPets(pets) => _.copy(pets = pets) -> none

        case Action.SetFavoriteDish(dish) =>
          _.copy(favoriteDish = dish) -> none

        case Action.IncrementCounter =>
          state => state.copy(counter = state.counter + 1) -> none

        case Action.DecrementCounter =>
          state => state.copy(counter = state.counter - 1) -> none

        case Action.SetActivity(activity) =>
          _.copy(bored = activity.some) -> none

        case Action.GetActivity =>
          _ -> ff4s
            .HttpClient[F]
            .get[Bored]("http://www.boredapi.com/api/activity")
            .map(activity => (Action.SetActivity(activity): Action).some)
            .some
      }
    }

    // establish websocket connection
    _ <- ff4s
      .WebSocketClient[F]
      .bidirectionalText(
        "wss://ws.postman-echo.com/raw/",
        _.evalMap { msg =>
          store.dispatch(Action.WebsocketMessageReceived(msg))
        },
        Stream.fromQueueUnterminated(wsSendQ)
      )
      .background

    // Fetch a new activity every 5 seconds.
    _ <- (Stream.unit ++ Stream.fixedDelay(5.second))
      .evalMap(_ => store.dispatch(Action.GetActivity))
      .compile
      .drain
      .background

    // We can also listen to and react to state changes.
    _ <- store.state.discrete
      .map(state => (state.favoriteDish, state.counter))
      .changes
      .filter { case (dish, counter) => dish == Dish.Ramen && counter == 3 }
      .evalMap(_ => store.dispatch(Action.Magic))
      .compile
      .drain
      .background

    rng <- Random.scalaUtilRandom.toResource

    // Animate the SVG with some random numbers.
    _ <- Stream
      .fixedDelay(1.second)
      .evalMap { _ =>
        val u = rng.betweenDouble(10.0, 90.0)
        (u, u).tupled
      }
      .evalMap { case (x, y) => store.dispatch(Action.SetSvgCoords(x, y)) }
      .compile
      .drain
      .background

  } yield store

}
