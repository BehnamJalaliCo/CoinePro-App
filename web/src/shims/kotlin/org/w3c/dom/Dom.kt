@file:Suppress("unused")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.w3c.dom

private fun parseJs(text: String): JsAny? =
    js("(function () { var d = new DOMParser().parseFromString(text, 'application/xml'); return d.getElementsByTagName('parsererror').length ? null : d; })()")
private fun childrenJs(n: JsAny): JsAny = js("n.childNodes")
private fun lengthJs(l: JsAny): Int = js("l.length")
private fun itemJs(l: JsAny, i: Int): JsAny = js("l[i]")
private fun nodeTypeJs(n: JsAny): Int = js("n.nodeType")
private fun nodeNameJs(n: JsAny): String = js("n.nodeName")
private fun localNameJs(n: JsAny): String? = js("n.localName || null")
private fun textJs(n: JsAny): String = js("n.textContent || ''")
private fun valueJs(n: JsAny): String? = js("n.nodeValue")
private fun attrJs(n: JsAny, name: String): String = js("(n.getAttribute && n.getAttribute(name)) || ''")
private fun hasAttrJs(n: JsAny, name: String): Boolean = js("!!(n.hasAttribute && n.hasAttribute(name))")
private fun byTagJs(n: JsAny, name: String): JsAny = js("n.getElementsByTagName(name)")
private fun docElementJs(d: JsAny): JsAny = js("d.documentElement")
private fun parentJs(n: JsAny): JsAny? = js("n.parentNode")

/** A node of the browser's DOM, as `org.w3c.dom` names it. */
open class Node internal constructor(internal val js: JsAny) {
    val nodeType: Short get() = nodeTypeJs(js).toShort()
    val nodeName: String get() = nodeNameJs(js)
    val localName: String? get() = localNameJs(js)
    open val textContent: String get() = textJs(js)
    val nodeValue: String? get() = valueJs(js)
    val childNodes: NodeList get() = NodeList(childrenJs(js))
    val firstChild: Node? get() = childNodes.let { if (it.length > 0) it.item(0) else null }
    val parentNode: Node? get() = parentJs(js)?.let { wrap(it) }
    fun hasChildNodes(): Boolean = childNodes.length > 0
    fun getNodeName(): String = nodeName
    fun getTextContent(): String = textContent

    companion object {
        const val ELEMENT_NODE: Short = 1
        const val ATTRIBUTE_NODE: Short = 2
        const val TEXT_NODE: Short = 3
        const val CDATA_SECTION_NODE: Short = 4
        const val COMMENT_NODE: Short = 8
        const val DOCUMENT_NODE: Short = 9
        internal fun wrap(js: JsAny): Node = if (nodeTypeJs(js) == 1) Element(js) else Node(js)
    }
}

class Element internal constructor(js: JsAny) : Node(js) {
    val tagName: String get() = nodeName
    fun getAttribute(name: String): String = attrJs(js, name)
    fun hasAttribute(name: String): Boolean = hasAttrJs(js, name)
    fun getElementsByTagName(name: String): NodeList = NodeList(byTagJs(js, name))
}

class Document internal constructor(js: JsAny) : Node(js) {
    val documentElement: Element get() = Element(docElementJs(js))
    fun getElementsByTagName(name: String): NodeList = NodeList(byTagJs(js, name))
    companion object {
        fun parse(text: String): Document = Document(parseJs(text) ?: throw org.xml.sax.SAXParseException("Malformed XML"))
    }
}

class NodeList internal constructor(private val list: JsAny) {
    val length: Int get() = lengthJs(list)
    fun getLength(): Int = length
    fun item(index: Int): Node? = if (index in 0 until length) Node.wrap(itemJs(list, index)) else null
}
