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

package examples.example2

sealed trait Action

object Action {

  case object Magic extends Action

  case class SetName(name: String) extends Action

  case class SetPets(pets: Pets) extends Action

  case class SetFavoriteDish(dish: Dish) extends Action

  case object IncrementCounter extends Action

  case object DecrementCounter extends Action

  case object GetActivity extends Action

  case class SetSvgCoords(x: Double, y: Double) extends Action

  case class SendWebsocketMessage(msg: String) extends Action

  case class WebsocketMessageReceived(msg: String) extends Action

}
