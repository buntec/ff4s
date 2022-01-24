package com.github.buntec.ff4s.examples

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {

  override def run: IO[Unit] = (new example1.App[IO]).run

}
