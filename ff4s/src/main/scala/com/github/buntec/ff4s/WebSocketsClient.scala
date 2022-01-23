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
        ws.onopen = (ev: dom.Event) => {
          println(s"onopen: $ev")
          dispatcher.unsafeRunAndForget(isOpen.complete(Right(())))
        }
        ws.onmessage = (ev: dom.MessageEvent) => {
          println(s"onmessage: $ev")
          ev.data match {
            case body: String =>
              dispatcher.unsafeRunAndForget(queue.offer(Some(body)))
            case _ => println(s"unknown data type: ${ev.data}")
          }
        }
        ws.onerror = (ev: dom.ErrorEvent) => {
          println(s"onerror: $ev")
          dispatcher.unsafeRunAndForget {
            shouldClose.complete(Left(new Exception(ev.toString)))
          }

        }
        ws.onclose = (ev: dom.CloseEvent) => {
          println(
            s"onclose: (reason: ${ev.reason}, code: ${ev.code}, wasClean: ${ev.wasClean})"
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
          println("finalizing websocket...")
          ws.close(1000);
        } *> onCloseCompleted.get.void)
    } yield ()).compile.drain

  }

}
