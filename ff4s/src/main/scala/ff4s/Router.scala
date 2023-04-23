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
import cats.effect.kernel.Resource
import cats.syntax.all._
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import org.http4s.Uri

trait Router[F[_]] {

  def navigateTo(uri: Uri): F[Unit]

  def location: Signal[F, Uri]

}

object Router {

  def apply[F[_]](
      window: fs2.dom.Window[F]
  )(implicit F: Async[F]): Resource[F, Router[F]] = {

    val history = window.history[Unit]

    val getLocation: F[Uri] =
      window.location.href.get.flatMap(Uri.fromString(_).liftTo[F])

    def mkAbsolute(uri: Uri): F[Uri] =
      window.location.href.get
        .flatMap(Uri.fromString(_).liftTo[F])
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

      def navigateTo(uri: Uri): F[Unit] = for {
        absUri <- mkAbsolute(uri)
        _ <- history.pushState((), absUri.renderString)
        _ <- loc.set(absUri)
      } yield ()

    }
  }

}
