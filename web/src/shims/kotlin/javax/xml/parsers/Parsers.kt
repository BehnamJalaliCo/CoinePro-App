@file:Suppress("unused", "UNUSED_PARAMETER")

package javax.xml.parsers

/** The browser's own `DOMParser`, behind the factory the phone's feed reader configures. */
class DocumentBuilderFactory private constructor() {
    var isNamespaceAware: Boolean = false
    var isExpandEntityReferences: Boolean = false
    var isValidating: Boolean = false
    var isIgnoringComments: Boolean = false
    var isCoalescing: Boolean = false
    var isXIncludeAware: Boolean = false
    fun setFeature(name: String, value: Boolean) {}
    fun setAttribute(name: String, value: Any?) {}
    fun newDocumentBuilder(): DocumentBuilder = DocumentBuilder()
    companion object { fun newInstance(): DocumentBuilderFactory = DocumentBuilderFactory() }
}

class ParserConfigurationException(message: String? = null) : Exception(message)

class DocumentBuilder internal constructor() {
    fun parse(stream: java.io.InputStream): org.w3c.dom.Document = parse(stream.readBytes().decodeToString())
    fun parse(source: org.xml.sax.InputSource): org.w3c.dom.Document = parse(source.text)
    fun parse(file: java.io.File): org.w3c.dom.Document = parse(file.readText())
    private fun parse(text: String): org.w3c.dom.Document = org.w3c.dom.Document.parse(text)
    fun setErrorHandler(handler: Any?) {}
}
