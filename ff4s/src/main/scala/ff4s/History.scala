package ff4s

import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all._
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import io.circe.Decoder
import io.circe.Encoder
import io.circe.scalajs._
import org.scalajs.dom

import scalajs.js

trait History[F[_], A] {

  def state: Signal[F, A]

  def pushState(a: A): F[Unit]

  def pushState(a: A, url: dom.URL): F[Unit]

}

object History {

  def apply[F[_], A: Encoder: Decoder](implicit
      F: Async[F]
  ): Resource[F, History[F, A]] = {

    def push(a: A) = F.delay {
      dom.window.history.pushState(a.asJsAny, "")
    }

    def pushUrl(a: A, url: dom.URL) = F.delay {
      dom.window.history.pushState(a.asJsAny, "", url.toString)
    }

    for {
      dispatcher <- Dispatcher[F]

      getState = F
        .delay(decodeJs[A](dom.window.history.state))
        .flatMap(F.fromEither(_))

      sr <- getState.flatMap(SignallingRef(_)).toResource

      listener: js.Function1[dom.PopStateEvent, Unit] =
        (ev: dom.PopStateEvent) =>
          decodeJs[A](ev.state).foreach { a =>
            dispatcher.unsafeRunAndForget(sr.set(a))
          }

      _ <- Resource.make(
        F.delay(dom.window.addEventListener("popstate", listener))
      )(_ => F.delay(dom.window.removeEventListener("popstate", listener)))

    } yield new History[F, A] {

      override def state: Signal[F, A] = sr

      override def pushState(a: A): F[Unit] = push(a)

      override def pushState(a: A, url: dom.URL): F[Unit] = pushUrl(a, url)

    }
  }

}
