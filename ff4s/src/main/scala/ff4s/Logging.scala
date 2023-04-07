/* Copyright 2022 buntec
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */

package ff4s

import scala.scalajs.js

import org.scalajs.dom.console

import scala.util.Try

sealed trait LogLevel {
  def level: Int
}

object LogLevel {

  case object Verbose extends LogLevel { val level = 4 }
  case object Debug extends LogLevel { val level = 3 }
  case object Info extends LogLevel { val level = 2 }
  case object Warn extends LogLevel { val level = 1 }
  case object Silent extends LogLevel { val level = 0 }

  def fromString(s: String): Option[LogLevel] = s.toLowerCase match {
    case "silent"            => Some(Silent)
    case "warn"              => Some(Warn)
    case "info"              => Some(Info)
    case "debug"             => Some(Debug)
    case "verbose" | "trace" => Some(Verbose)
    case _                   => None
  }

}

private[ff4s] object Logging {

  val level: LogLevel = Try(
    js.Dynamic.global.ff4s.logLevel.asInstanceOf[String]
  ).toOption.flatMap(LogLevel.fromString).getOrElse(LogLevel.Warn)

  def verbose(msg: => String): Unit =
    if (level.level >= LogLevel.Verbose.level) console.log(msg)

  def debug(msg: => String): Unit =
    if (level.level >= LogLevel.Debug.level) console.log(msg)

  def info(msg: => String): Unit =
    if (level.level >= LogLevel.Info.level) console.info(msg)

  def warn(msg: => String): Unit =
    if (level.level >= LogLevel.Warn.level) console.info(msg)

  def error(msg: => String): Unit = console.error(msg)

}
