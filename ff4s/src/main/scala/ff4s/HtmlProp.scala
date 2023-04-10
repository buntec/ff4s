package ff4s

import ff4s.codecs.Codec

class HtmlProp[V, DomV](val name: String, val codec: Codec[V, DomV])
