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

package examples.example1

import cats.effect.Concurrent
import cats.syntax.all._

object Store {

  def apply[F[_]: Concurrent] = ff4s.Store[F, State, Action](State()) {
    _ match {
      case Action.AddTodo =>
        state => {
          val nextId = state.nextId
          state.todoInput match {
            case Some(what) if what.nonEmpty =>
              state.copy(
                nextId = nextId + 1,
                todos = state.todos :+ Todo(what, nextId),
                todoInput = None
              ) -> none
            case _ => state -> none
          }
        }

      case Action.RemoveTodo(id) =>
        state => state.copy(todos = state.todos.filterNot(_.id == id)) -> none

      case Action.SetTodoInput(what) => _.copy(todoInput = Some(what)) -> none
    }
  }

}
