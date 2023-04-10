package ff4s

import ff4s.codecs.Codec

class AriaAttr[V](val name: String, val codec: Codec[V, String])
