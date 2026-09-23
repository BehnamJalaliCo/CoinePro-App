package org.xml.sax

class InputSource(reader: java.io.Reader) {
    internal val text: String = reader.readText()
    var encoding: String? = null
}

open class SAXException(message: String? = null) : Exception(message)
class SAXParseException(message: String? = null) : SAXException(message)
