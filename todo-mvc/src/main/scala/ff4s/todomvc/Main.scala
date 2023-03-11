/*
 * Copyright 2022 buntec
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ff4s.todomvc

import cats.effect.{IO, IOApp}
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.Scheduler
import cats.effect.unsafe.IORuntimeConfig
import scala.scalajs.concurrent.QueueExecutionContext

object Main extends IOApp.Simple {

  private val microtaskEC = QueueExecutionContext.promises()

  // @annotation.nowarn("msg=never used")
  private val ioRuntimeWithMicrotaskEC = IORuntime.apply(
    microtaskEC,
    microtaskEC,
    Scheduler.createDefaultScheduler()._1,
    () => (),
    IORuntimeConfig()
  )

  override protected def runtime: IORuntime = ioRuntimeWithMicrotaskEC

  override def run: IO[Unit] = new App[IO].run

}
