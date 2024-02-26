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

import ff4s.codecs.Codec

final class SvgAttr[V](
    val name: String,
    val codec: Codec[V, String],
    val namespace: Option[String]
)

object SvgAttr {

  def apply[V](
      name: String,
      codec: Codec[V, String],
      namespace: Option[String]
  ): SvgAttr[V] = new SvgAttr[V](name, codec, namespace)
}