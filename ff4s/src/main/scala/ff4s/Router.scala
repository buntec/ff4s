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

import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.syntax.all._
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import org.http4s.Uri
import org.scalajs.dom

trait Router[F[_]] {

  def navigateTo(uri: Uri): F[Unit]

  def location: Signal[F, Uri]

}

object Router {

  def apply[F[_]](history: History[F, Unit])(implicit F: Async[F]) = {

    val getLocation: F[Uri] = F
      .delay(dom.window.location.href)
      .flatMap(Uri.fromString(_).liftTo[F])

    def mkAbsolute(uri: Uri): F[Uri] =
      F.delay(dom.window.location.toString)
        .flatMap(Uri.fromString(_).liftTo)
        .map(_.resolve(uri))

    for {
      loc <- getLocation.flatMap(SignallingRef(_)).toResource
      _ <- history.state.discrete
        .evalMap(_ => getLocation.flatMap(loc.set))
        .compile
        .drain
        .background
    } yield new Router[F] {

      override def location: Signal[F, Uri] = loc

      override def navigateTo(uri: Uri): F[Unit] = for {
        absUri <- mkAbsolute(uri)
        url <- F.catchNonFatal(new dom.URL(absUri.renderString))
        _ <- history.pushState((), url)
        _ <- loc.set(absUri)
      } yield ()

    }
  }

}
