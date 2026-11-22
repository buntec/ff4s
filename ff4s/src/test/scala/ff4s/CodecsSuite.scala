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

import ff4s.codecs._

class CodecsSuite extends munit.FunSuite {

  test("Int and Double string codecs round-trip") {
    assertEquals(IntAsStringCodec.encode(42), "42")
    assertEquals(IntAsStringCodec.decode("-7"), -7)
    assertEquals(
      DoubleAsStringCodec.decode(DoubleAsStringCodec.encode(1.5)),
      1.5
    )
  }

  test("boolean attribute-presence codec") {
    assertEquals(BooleanAsAttrPresenceCodec.encode(true), "")
    assertEquals(BooleanAsAttrPresenceCodec.encode(false), null)
    assert(BooleanAsAttrPresenceCodec.decode(""))
    assert(!BooleanAsAttrPresenceCodec.decode(null))
  }

  test("boolean string codecs") {
    val cases = List(
      (BooleanAsTrueFalseStringCodec, "true", "false"),
      (BooleanAsYesNoStringCodec, "yes", "no"),
      (BooleanAsOnOffStringCodec, "on", "off")
    )
    cases.foreach { case (codec, t, f) =>
      assertEquals(codec.encode(true), t)
      assertEquals(codec.encode(false), f)
      assert(codec.decode(t))
      assert(!codec.decode(f))
    }
  }

}
