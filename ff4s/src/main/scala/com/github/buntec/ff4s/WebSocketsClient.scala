package com.github.buntec.ff4s

import org.scalajs.dom

import cats.syntax.all._

import cats.effect.kernel.Async
import cats.effect.kernel.Deferred
import cats.effect.std.Queue
import cats.effect.std.Dispatcher

import fs2.Stream

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
    ): F[Unit] = (for {
      dispatcher <- Stream.resource(Dispatcher[F])
      isOpen <- Stream.eval(Deferred[F, Either[Throwable, Unit]])
      shouldClose <- Stream.eval(Deferred[F, Either[Throwable, Unit]])
      onCloseCompleted <- Stream.eval(Deferred[F, Either[Throwable, Unit]])
      queue <- Stream.eval(Queue.bounded[F, Option[String]](100))
      ws <- Stream.eval(Async[F].delay {
        val ws = new dom.WebSocket(uri)
        ws.onopen = (_: dom.Event) => {
          Logging.debug(s"websocket onopen")
          dispatcher.unsafeRunAndForget(isOpen.complete(Right(())))
        }
        ws.onmessage = (ev: dom.MessageEvent) => {
          Logging.debug(s"websocket onmessage: ${ev.data}")
          ev.data match {
            case body: String =>
              dispatcher.unsafeRunAndForget(queue.offer(Some(body)))
            case _ =>
              Logging.warn(
                s"websocket client cannot handle binary frames: ${ev.data}. will close connection..."
              )
              dispatcher.unsafeRunAndForget(
                queue.offer(None) *> shouldClose.complete(Right(()))
              )
          }
        }
        ws.onerror = (ev: dom.ErrorEvent) => {
          Logging.debug(s"websocket onerror: ${ev.message}")
          dispatcher.unsafeRunAndForget {
            shouldClose.complete(Left(new Exception(ev.toString)))
          }

        }
        ws.onclose = (ev: dom.CloseEvent) => {
          Logging.debug(
            s"websocket onclose: (reason: ${ev.reason}, code: ${ev.code}, wasClean: ${ev.wasClean})"
          )
          dispatcher.unsafeRunAndForget(
            queue.offer(None) *> onCloseCompleted.complete(Right(()))
          )
        }
        ws
      })
      _ <- Stream
        .fromQueueNoneTerminated(queue)
        .interruptWhen(shouldClose)
        .through(pipe)
        .evalMap(msg =>
          Async[F].delay {
            ws.send(msg)
          }
        )
        .onFinalize(Async[F].delay {
          Logging.debug("finalizing websocket connection...")
          ws.close(1000)
        } *> onCloseCompleted.get.void)
    } yield ()).compile.drain

  }

}
