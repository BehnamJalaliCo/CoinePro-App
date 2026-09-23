package java.io

open class Reader(private val text: String = "") : Closeable {
    open fun readText(): String = text
    override fun close() {}
}

class StringReader(text: String) : Reader(text)
class InputStreamReader(stream: InputStream, charset: Any? = null) : Reader(stream.readBytes().decodeToString())
fun Reader.buffered(): BufferedReader = BufferedReader(this)
class BufferedReader(private val inner: Reader) : Reader() {
    private var lines: ArrayDeque<String>? = null
    override fun readText(): String = inner.readText()
    fun readLine(): String? {
        val queue = lines ?: ArrayDeque(inner.readText().lines()).also { lines = it }
        return queue.removeFirstOrNull()
    }
    fun readLines(): List<String> = readText().lines()
    fun lineSequence(): Sequence<String> = readText().lineSequence()
    fun <T> useLines(block: (Sequence<String>) -> T): T = block(lineSequence())
}

fun Reader.readText(): String = this.readText()
