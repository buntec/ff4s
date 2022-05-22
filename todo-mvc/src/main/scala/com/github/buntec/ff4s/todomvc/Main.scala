package com.github.buntec.ff4s.todomvc

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {

  override def run: IO[Unit] = new App[IO].run

}
