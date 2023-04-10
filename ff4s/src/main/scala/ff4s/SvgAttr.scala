package ff4s

import ff4s.codecs.Codec

class SvgAttr[V](
    val name: String,
    val codec: Codec[V, String],
    val namespace: Option[String]
)
