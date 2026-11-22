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

package ff4s

import cats.effect.IO

import scala.concurrent.duration._

object TestUtils {

  /** Re-evaluates `check` until it yields `true`, failing after `timeout`. */
  def waitUntil(
      check: IO[Boolean],
      timeout: FiniteDuration = 10.seconds,
      interval: FiniteDuration = 5.millis
  ): IO[Unit] = {
    // check first, and only sleep before retrying
    def loop: IO[Unit] = check.ifM(IO.unit, IO.sleep(interval) *> loop)
    loop.timeoutTo(
      timeout,
      IO.raiseError(
        new AssertionError(s"condition not met within $timeout")
      )
    )
  }

}
