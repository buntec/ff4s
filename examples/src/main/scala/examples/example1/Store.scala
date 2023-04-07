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

object Store {

  def apply[F[_]: Concurrent] = ff4s.Store[F, State, Action](State()) { ref =>
    _ match {
      case Action.AddTodo =>
        ref.update { state =>
          val nextId = state.nextId
          state.todoInput match {
            case Some(what) if what.nonEmpty =>
              state.copy(
                nextId = nextId + 1,
                todos = state.todos :+ Todo(what, nextId),
                todoInput = None
              )
            case _ => state
          }
        }

      case Action.RemoveTodo(id) =>
        ref.update { state =>
          state.copy(todos = state.todos.filterNot(_.id == id))
        }

      case Action.SetTodoInput(what) =>
        ref.update(_.copy(todoInput = Some(what)))
    }
  }

}
