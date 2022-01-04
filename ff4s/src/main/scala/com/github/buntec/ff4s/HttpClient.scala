package com.github.buntec.ff4s

import cats.syntax.all._

import cats.effect.kernel.Async

import io.circe.{Encoder, Decoder}

import sttp.client3._
import sttp.client3.circe._
import sttp.client3.impl.cats.FetchCatsBackend
import sttp.model.Uri
import sttp.ws.WebSocket

trait HttpClient[F[_]] {

  def get[R: Decoder](url: String): F[R]

  def post[R: Decoder, P: Encoder](url: String, payload: P): F[R]

  def websocket(
      url: String,
      handler: String => F[Option[String]]
  ): F[Unit]

}

object HttpClient {

  def apply[F[_]: Async]: HttpClient[F] = {

    val backend = FetchCatsBackend[F]()

    new HttpClient[F] {

      override def post[R: Decoder, P: Encoder](url: String, payload: P): F[R] =
        basicRequest
          .post(Uri.unsafeParse(url))
          .body(payload)
          .response(asJson[R])
          .send(backend)
          .flatMap(res => Async[F].fromEither(res.body))

      def websocket(
          url: String,
          handler: String => F[Option[String]]
      ): F[Unit] = basicRequest
        .get(Uri.unsafeParse(url))
        .response(asWebSocketAlways { ws: WebSocket[F] =>
          fs2.Stream
            .repeatEval(ws.receiveText())
            .evalMap(handler)
            .collect { case Some(response) => response }
            .evalMap(response => ws.sendText(response))
            .compile
            .drain
        })
        .send(backend)
        .void

      override def get[R: Decoder](url: String): F[R] = {
        val uri = Uri.unsafeParse(url)
        basicRequest
          .get(uri)
          .response(asJson[R])
          .send(backend)
          .flatMap(res => Async[F].fromEither(res.body))
      }

    }
  }

}
