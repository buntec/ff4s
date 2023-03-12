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

package todomvc

import scala.collection.immutable.IntMap

case class State(
    todos: Map[Int, Todo] = IntMap.empty[Todo],
    nextId: Int = 0,
    todoInput: Option[String] = None,
    filter: Filter = All
)

case class Todo(what: String, id: Int, complete: Boolean, isEdit: Boolean)

sealed trait Filter {
  def pred: Todo => Boolean
}

case object All extends Filter {
  override def pred = (_ => true)
}
case object Active extends Filter {
  override def pred = (todo => !todo.complete)
}

case object Completed extends Filter {
  override def pred = (_.complete)
}

object Filter {
  val values: List[Filter] = List(All, Active, Completed)
}
