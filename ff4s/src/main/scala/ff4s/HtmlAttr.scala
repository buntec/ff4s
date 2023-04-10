package ff4s

import ff4s.codecs.Codec

class HtmlAttr[V](val name: String, val codec: Codec[V, String])
