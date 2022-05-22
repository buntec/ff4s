package ff4s

import cats.effect.kernel.Async
import cats.syntax.all._
import io.circe.Decoder
import io.circe.Encoder
import org.http4s.Method
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dom.FetchClientBuilder

trait HttpClient[F[_]] {

  def get[R: Decoder](url: String): F[R]

  def post[R: Decoder, P: Encoder](url: String, payload: P): F[R]

}

object HttpClient {

  def apply[F[_]](implicit F: Async[F]): HttpClient[F] = {

    val client = FetchClientBuilder[F].create

    val dsl = Http4sClientDsl[F]

    import dsl._

    new HttpClient[F] {

      override def post[R: Decoder, P: Encoder](
          url: String,
          payload: P
      ): F[R] =
        F.fromEither(Uri.fromString(url))
          .flatMap(uri => client.expect[R](Method.POST(payload, uri)))

      override def get[R: Decoder](url: String): F[R] =
        F.fromEither(Uri.fromString(url))
          .flatMap(uri => client.expect[R](Method.GET(uri)))

    }
  }

}
