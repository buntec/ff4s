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

import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.syntax.all._
import fs2.Stream
import io.circe.Decoder
import io.circe.Encoder
import io.circe.parser._
import io.circe.syntax._
import org.http4s.Uri
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket._
import org.http4s.dom._

trait WebSocketsClient[F[_]] {

  def receiveJson[R: Decoder](
      uri: String,
      receive: Stream[F, R] => Stream[F, Unit]
  ): F[Unit] = bidirectionalJson[R, Unit](uri, receive, Stream.empty)

  def bidirectionalJson[R: Decoder, S: Encoder](
      uri: String,
      receive: Stream[F, R] => Stream[F, Unit],
      send: Stream[F, S]
  ): F[Unit]

  def receiveText(
      uri: String,
      receive: Stream[F, String] => Stream[F, Unit]
  ): F[Unit] = bidirectionalText(uri, receive, Stream.empty)

  def bidirectionalText(
      uri: String,
      receive: Stream[F, String] => Stream[F, Unit],
      send: Stream[F, String]
  ): F[Unit]

}

object WebSocketsClient {

  def apply[F[_]](implicit F: Async[F]): WebSocketsClient[F] =
    new WebSocketsClient[F] {

      override def bidirectionalJson[R: Decoder, S: Encoder](
          uri: String,
          receive: Stream[F, R] => Stream[F, Unit],
          send: Stream[F, S]
      ): F[Unit] = {

        val receive0 = (ts: Stream[F, String]) =>
          receive(ts.evalMap { text => F.fromEither(decode[R](text)) })

        val send0 = send.map(s => s.asJson.noSpaces)

        bidirectionalText(uri, receive0, send0)

      }

      override def bidirectionalText(
          uri: String,
          receive: Stream[F, String] => Stream[F, Unit],
          send: Stream[F, String]
      ): F[Unit] = {

        Async[F].fromEither(Uri.fromString(uri)).flatMap { uri =>
          WebSocketClient[F]
            .connectHighLevel(WSRequest(uri))
            .use { conn =>
              (
                conn.receiveStream
                  .collect { case Text(data, _) => data }
                  .through(receive)
                  .compile
                  .drain,
                send
                  .map(WSFrame.Text(_))
                  .through(conn.sendPipe)
                  .compile
                  .drain
              ).parTupled.void
            }
        }
      }

    }

}
