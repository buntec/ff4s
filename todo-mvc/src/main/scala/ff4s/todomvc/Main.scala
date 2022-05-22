package ff4s.todomvc

import cats.effect.{IO, IOApp}
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.Scheduler
import cats.effect.unsafe.IORuntimeConfig
import scala.scalajs.concurrent.QueueExecutionContext

object Main extends IOApp.Simple {

  private val microtaskEC = QueueExecutionContext.promises()

  @annotation.nowarn("msg=never used")
  private val ioRuntimeWithMicrotaskEC = IORuntime.apply(
    microtaskEC,
    microtaskEC,
    Scheduler.createDefaultScheduler()._1,
    () => (),
    IORuntimeConfig()
  )

  // override protected def runtime: IORuntime = ioRuntimeWithMicrotaskEC

  override def run: IO[Unit] = new App[IO].run

}
