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

import cats.effect.kernel.Resource

trait App[F[_], State, Action] extends Dsl[F, State, Action] {

  /* The id of the "root" DOM node into which this app will be rendered. */
  def rootElementId = "app"

  /* The top-level component of this app. */
  def view: V

  def store: Resource[F, Store[F, State, Action]]

}
