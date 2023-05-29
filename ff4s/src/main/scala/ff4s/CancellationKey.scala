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

import cats.Applicative
import cats.Eq
import cats.effect.Unique
import cats.syntax.all._

final case class CancellationKey private[ff4s] (
    private[ff4s] val token: Unique.Token
)

object CancellationKey {

  /** Generates a unique cancellation key. */
  def apply[F[_]: Unique: Applicative]: F[CancellationKey] =
    Unique[F].unique.map(new CancellationKey(_))

  implicit val eq: Eq[CancellationKey] = Eq.fromUniversalEquals

}
