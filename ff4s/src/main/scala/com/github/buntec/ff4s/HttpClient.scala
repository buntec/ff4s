package com.github.buntec.ff4s

import cats.syntax.all._

import cats.effect.kernel.Async

import io.circe.{Encoder, Decoder}

import sttp.client3._
import sttp.client3.circe._
import sttp.client3.impl.cats.FetchCatsBackend
import sttp.model.Uri

trait HttpClient[F[_]] {

  def get[R: Decoder](url: String): F[R]

  def post[R: Decoder, P: Encoder](url: String, payload: P): F[R]

}

object HttpClient {

  def apply[F[_]: Async]: HttpClient[F] = {

    val backend = FetchCatsBackend[F]()

    new HttpClient[F] {

      override def post[R: Decoder, P: Encoder](url: String, payload: P): F[R] =
        Async[F].defer { // defer b/c constructor could throw
          basicRequest
            .post(Uri.unsafeParse(url))
            .body(payload)
            .response(asJson[R])
            .send(backend)
            .flatMap(res => Async[F].fromEither(res.body))
        }

      override def get[R: Decoder](url: String): F[R] =
        Async[F].defer { // defer b/c constructor could throw
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
