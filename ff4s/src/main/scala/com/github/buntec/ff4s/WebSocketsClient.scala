package com.github.buntec.ff4s

import cats.effect.kernel.Async
import cats.syntax.all._
import fs2.Stream
import org.http4s.Uri
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket._
import org.http4s.dom._

trait WebSocketsClient[F[_]] {

  def stream(
      uri: String,
      pipe: Stream[F, String] => Stream[F, String]
  ): F[Unit]

}

object WebSocketsClient {

  def apply[F[_]: Async]: WebSocketsClient[F] = new WebSocketsClient[F] {

    override def stream(
        uri: String,
        pipe: Stream[F, String] => Stream[F, String]
    ): F[Unit] =
      Async[F].fromEither(Uri.fromString(uri)).flatMap { uri =>
        WebSocketClient[F]
          .connectHighLevel(WSRequest(uri))
          .use { conn =>
            conn.receiveStream
              .collect { case Text(data, _) =>
                data
              }
              .through(pipe)
              .evalMap(conn.sendText(_))
              .compile
              .drain
          }
      }
  }

}
