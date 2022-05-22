package com.github.buntec.ff4s.examples

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {

  override def run: IO[Unit] = (new example2.App[IO]).run

}
