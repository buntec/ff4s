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

/** A [[Slot]] represents a special child component of web components.
  *
  * Many web components reserve a `slot` attribute for some of their children,
  * with a particular meaning.
  *
  * In order to have compile-time fixed slots for your elements, you can define
  * a variable with their name, and it will allow you to attach child in a
  * simple manner.
  */
final case class Slot(name: String)

object Slot {
  def apply(name: String) = new Slot(name)
}
