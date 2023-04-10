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

package object codecs {

  def AsIsCodec[V](): Codec[V, V] = new Codec[V, V] {
    override def encode(scalaValue: V): V = scalaValue

    override def decode(domValue: V): V = domValue
  }

  // String Codecs

  val StringAsIsCodec: Codec[String, String] = AsIsCodec()

  // Int Codecs

  val IntAsIsCodec: Codec[Int, Int] = AsIsCodec()

  lazy val IntAsStringCodec: Codec[Int, String] = new Codec[Int, String] {

    override def decode(domValue: String): Int =
      domValue.toInt // @TODO this can throw exception. How do we handle this?

    override def encode(scalaValue: Int): String = scalaValue.toString
  }

  // Double Codecs

  lazy val DoubleAsIsCodec: Codec[Double, Double] = AsIsCodec()

  lazy val DoubleAsStringCodec: Codec[Double, String] =
    new Codec[Double, String] {

      override def decode(domValue: String): Double =
        domValue.toDouble // @TODO this can throw exception. How do we handle this?

      override def encode(scalaValue: Double): String = scalaValue.toString
    }

  // Boolean Codecs

  val BooleanAsIsCodec: Codec[Boolean, Boolean] = AsIsCodec()

  val BooleanAsAttrPresenceCodec: Codec[Boolean, String] =
    new Codec[Boolean, String] {

      override def decode(domValue: String): Boolean = domValue != null

      override def encode(scalaValue: Boolean): String =
        if (scalaValue) "" else null
    }

  lazy val BooleanAsTrueFalseStringCodec: Codec[Boolean, String] =
    new Codec[Boolean, String] {

      override def decode(domValue: String): Boolean = domValue == "true"

      override def encode(scalaValue: Boolean): String =
        if (scalaValue) "true" else "false"
    }

  lazy val BooleanAsYesNoStringCodec: Codec[Boolean, String] =
    new Codec[Boolean, String] {

      override def decode(domValue: String): Boolean = domValue == "yes"

      override def encode(scalaValue: Boolean): String =
        if (scalaValue) "yes" else "no"
    }

  lazy val BooleanAsOnOffStringCodec: Codec[Boolean, String] =
    new Codec[Boolean, String] {

      override def decode(domValue: String): Boolean = domValue == "on"

      override def encode(scalaValue: Boolean): String =
        if (scalaValue) "on" else "off"
    }

}
