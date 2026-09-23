@file:Suppress("unused")

package java.io

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
class FileNotFoundException(message: String? = null) : IOException(message)
class EOFException(message: String? = null) : IOException(message)
class UncheckedIOException(message: String?, cause: IOException?) : RuntimeException(message, cause)
interface Serializable
interface Closeable : AutoCloseable { override fun close() }
interface Flushable { fun flush() }

open class OutputStream : Closeable {
    open fun write(b: Int) {}
    open fun write(b: ByteArray) { b.forEach { write(it.toInt()) } }
    open fun write(b: ByteArray, off: Int, len: Int) { for (i in off until off + len) write(b[i].toInt()) }
    open fun flush() {}
    override fun close() {}
}

class ByteArrayOutputStream(size: Int = 32) : OutputStream() {
    private var bytes = ByteArray(maxOf(size, 16))
    private var count = 0
    private fun ensure(extra: Int) { if (count + extra > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, count + extra)) }
    override fun write(b: Int) { ensure(1); bytes[count++] = b.toByte() }
    override fun write(b: ByteArray, off: Int, len: Int) { ensure(len); b.copyInto(bytes, count, off, off + len); count += len }
    override fun write(b: ByteArray) = write(b, 0, b.size)
    fun toByteArray(): ByteArray = bytes.copyOf(count)
    fun size(): Int = count
    fun reset() { count = 0 }
    fun writeTo(out: OutputStream) = out.write(toByteArray())
    override fun toString(): String = toByteArray().decodeToString()
    fun toString(charset: String): String = toString()
}

open class InputStream : Closeable {
    open fun read(): Int = -1
    open fun read(b: ByteArray): Int = read(b, 0, b.size)
    open fun read(b: ByteArray, off: Int, len: Int): Int {
        var n = 0
        while (n < len) { val c = read(); if (c < 0) break; b[off + n] = c.toByte(); n++ }
        return if (n == 0 && len > 0) -1 else n
    }
    open fun readBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) { val c = read(); if (c < 0) break; out.write(c) }
        return out.toByteArray()
    }
    open fun available(): Int = 0
    fun readAllBytes(): ByteArray = readBytes()
    fun readNBytes(len: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        while (out.size() < len) { val c = read(); if (c < 0) break; out.write(c) }
        return out.toByteArray()
    }
    fun bufferedReader(charset: Any? = null): BufferedReader = BufferedReader(StringReader(readBytes().decodeToString()))
    fun reader(charset: Any? = null): Reader = StringReader(readBytes().decodeToString())
    fun copyTo(out: OutputStream, bufferSize: Int = 8192): Long { val b = readBytes(); out.write(b); return b.size.toLong() }
    override fun close() {}
}

class ByteArrayInputStream(private val buf: ByteArray) : InputStream() {
    private var pos = 0
    override fun read(): Int = if (pos < buf.size) buf[pos++].toInt() and 0xff else -1
    override fun readBytes(): ByteArray = buf.copyOfRange(pos, buf.size).also { pos = buf.size }
}

open class Writer : Closeable {
    open fun write(s: String) {}
    open fun append(s: CharSequence?): Writer = apply { write(s.toString()) }
    open fun flush() {}
    override fun close() {}
}

class StringWriter : Writer() {
    private val sb = StringBuilder()
    override fun write(s: String) { sb.append(s) }
    override fun toString(): String = sb.toString()
    fun getBuffer(): StringBuilder = sb
}

class PrintWriter(private val out: Writer) : Writer() {
    constructor(stream: OutputStream) : this(StreamWriter(stream))
    override fun write(s: String) { out.write(s) }
    fun print(s: Any?) { out.write(s.toString()) }
    fun println(s: Any? = "") { out.write(s.toString() + "\n") }
}

/*
 * Files, in the page's storage. The phone keeps a handful of small files of its own — the log, the
 * last crash, a drawing's picture — and so does the page: each file is one `localStorage` entry,
 * `file:<path>`, its bytes in base64. A directory is simply the prefix its files share.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun fileGetJs(key: String): String? = js("(function () { try { return localStorage.getItem(key); } catch (e) { return null; } })()")
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun fileSetJs(key: String, value: String): Boolean = js("(function () { try { localStorage.setItem(key, value); return true; } catch (e) { return false; } })()")
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun fileRemoveJs(key: String): Unit = js("(function () { try { localStorage.removeItem(key); } catch (e) {} })()")
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun fileKeysJs(prefix: String): String = js("(function () { var out = []; try { for (var i = 0; i < localStorage.length; i++) { var k = localStorage.key(i); if (k && k.indexOf(prefix) === 0) out.push(k.substring(prefix.length)); } } catch (e) {} return out.join('\\n'); })()")

private const val FILE_PREFIX = "file:"
private const val STAMP_PREFIX = "filetime:"

class File(path: String) : Comparable<File> {
    constructor(parent: File?, child: String) : this((parent?.path?.trimEnd('/') ?: "") + "/" + child)
    constructor(parent: String?, child: String) : this((parent?.trimEnd('/') ?: "") + "/" + child)

    val path: String = normalise(path)
    val name: String get() = path.substringAfterLast('/')
    val absolutePath: String get() = path
    val canonicalPath: String get() = path
    val absoluteFile: File get() = this
    val parent: String? get() = path.substringBeforeLast('/', "").ifEmpty { null }
    val parentFile: File? get() = parent?.let(::File)
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val isFile: Boolean get() = fileGetJs(FILE_PREFIX + path) != null
    val isDirectory: Boolean get() = !isFile && children().isNotEmpty()

    private fun children(): List<String> {
        val prefix = path.trimEnd('/') + "/"
        val raw = fileKeysJs(FILE_PREFIX + prefix)
        return if (raw.isEmpty()) emptyList() else raw.split('\n')
    }

    fun exists(): Boolean = isFile || isDirectory
    fun canRead(): Boolean = exists()
    fun canWrite(): Boolean = true
    fun createNewFile(): Boolean = if (isFile) false else { writeBytes(ByteArray(0)); true }
    fun delete(): Boolean {
        val had = isFile
        fileRemoveJs(FILE_PREFIX + path); fileRemoveJs(STAMP_PREFIX + path)
        return had
    }
    fun deleteRecursively(): Boolean {
        children().forEach { File(path.trimEnd('/') + "/" + it).delete() }
        delete()
        return true
    }
    fun deleteOnExit() {}
    fun mkdirs(): Boolean = true
    fun mkdir(): Boolean = true
    fun length(): Long = if (isFile) readBytes().size.toLong() else 0L
    fun lastModified(): Long = fileGetJs(STAMP_PREFIX + path)?.toLongOrNull() ?: 0L
    fun setLastModified(time: Long): Boolean = fileSetJs(STAMP_PREFIX + path, time.toString())
    fun renameTo(dest: File): Boolean {
        if (!isFile) return false
        dest.writeBytes(readBytes())
        delete()
        return true
    }
    fun listFiles(): Array<File>? = if (!isDirectory) null else
        children().map { it.substringBefore('/') }.distinct().map { File(path.trimEnd('/') + "/" + it) }.toTypedArray()
    fun listFiles(filter: (File) -> Boolean): Array<File>? = listFiles()?.filter(filter)?.toTypedArray()
    fun list(): Array<String>? = listFiles()?.map { it.name }?.toTypedArray()
    val freeSpace: Long get() = 5L * 1024 * 1024
    val usableSpace: Long get() = freeSpace
    val totalSpace: Long get() = freeSpace

    fun readBytes(): ByteArray {
        val raw = fileGetJs(FILE_PREFIX + path) ?: throw FileNotFoundException("$path (No such file or directory)")
        return java.util.Base64.getDecoder().decode(raw)
    }
    fun readText(charset: Any? = null): String = readBytes().decodeToString()
    fun readLines(charset: Any? = null): List<String> = readText().lines().let { if (it.lastOrNull() == "") it.dropLast(1) else it }
    fun forEachLine(charset: Any? = null, action: (String) -> Unit) = readLines().forEach(action)
    fun writeBytes(bytes: ByteArray) {
        if (!fileSetJs(FILE_PREFIX + path, java.util.Base64.getEncoder().encodeToString(bytes))) throw IOException("No space left for $path")
        fileSetJs(STAMP_PREFIX + path, com.coinepro.web.jvm.nowMillisJs().toLong().toString())
    }
    fun writeText(text: String, charset: Any? = null) = writeBytes(text.encodeToByteArray())
    fun appendText(text: String, charset: Any? = null) = appendBytes(text.encodeToByteArray())
    fun appendBytes(bytes: ByteArray) = writeBytes((if (isFile) readBytes() else ByteArray(0)) + bytes)
    fun outputStream(): OutputStream = FileOutputStream(this)
    fun inputStream(): InputStream = FileInputStream(this)
    fun bufferedReader(charset: Any? = null): BufferedReader = BufferedReader(StringReader(readText()))
    fun reader(charset: Any? = null): Reader = StringReader(readText())
    fun bufferedWriter(charset: Any? = null): Writer = FileWriter(this)
    fun writer(charset: Any? = null): Writer = FileWriter(this)
    fun toURI(): java.net.URI = java.net.URI("file://$path")
    fun resolve(relative: String): File = File(this, relative)
    fun copyTo(target: File, overwrite: Boolean = false): File = target.also { it.writeBytes(readBytes()) }
    fun walkTopDown(): Sequence<File> = sequenceOf(this) + (listFiles()?.asSequence()?.flatMap { it.walkTopDown() } ?: emptySequence())
    fun walk(): Sequence<File> = walkTopDown()
    override fun compareTo(other: File): Int = path.compareTo(other.path)
    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()
    override fun toString(): String = path

    companion object {
        const val separator: String = "/"
        const val separatorChar: Char = '/'
        fun createTempFile(prefix: String, suffix: String?, directory: File? = null): File =
            File(directory ?: File("/cache"), prefix + com.coinepro.web.jvm.nowMillisJs().toLong() + (suffix ?: ".tmp"))
        private fun normalise(p: String): String = p.replace(Regex("/+"), "/").let { if (it.length > 1) it.trimEnd('/') else it }
    }
}

class FileOutputStream(private val file: File, private val append: Boolean = false) : OutputStream() {
    constructor(path: String, append: Boolean = false) : this(File(path), append)
    private val buffer = ByteArrayOutputStream()
    override fun write(b: Int) { buffer.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) { buffer.write(b, off, len) }
    override fun flush() {
        val bytes = buffer.toByteArray()
        if (append) file.appendBytes(bytes) else file.writeBytes(bytes)
    }
    override fun close() = flush()
    val fd: Any? get() = null
}

class FileInputStream(file: File) : InputStream() {
    constructor(path: String) : this(File(path))
    private val inner = ByteArrayInputStream(file.readBytes())
    override fun read(): Int = inner.read()
    override fun readBytes(): ByteArray = inner.readBytes()
}

class FileWriter(private val file: File, private val append: Boolean = false) : Writer() {
    private val sb = StringBuilder()
    override fun write(s: String) { sb.append(s) }
    override fun flush() { if (append) file.appendText(sb.toString()) else file.writeText(sb.toString()) }
    override fun close() = flush()
}

val File.nameWithoutExtensionCompat: String get() = nameWithoutExtension
operator fun File.div(child: String): File = File(this, child)

internal class StreamWriter(private val stream: OutputStream) : Writer() {
    override fun write(s: String) = stream.write(s.encodeToByteArray())
    override fun flush() = stream.flush()
    override fun close() = stream.close()
}

class OutputStreamWriter(stream: OutputStream, charset: Any? = null) : Writer() {
    private val inner = StreamWriter(stream)
    override fun write(s: String) = inner.write(s)
    override fun flush() = inner.flush()
    override fun close() = inner.close()
}

class BufferedWriter(private val inner: Writer) : Writer() {
    override fun write(s: String) = inner.write(s)
    fun newLine() = inner.write("\n")
    override fun flush() = inner.flush()
    override fun close() = inner.close()
}

class BufferedOutputStream(private val inner: OutputStream, size: Int = 8192) : OutputStream() {
    override fun write(b: Int) = inner.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = inner.write(b, off, len)
    override fun flush() = inner.flush()
    override fun close() = inner.close()
}

class BufferedInputStream(private val inner: InputStream, size: Int = 8192) : InputStream() {
    override fun read(): Int = inner.read()
    override fun readBytes(): ByteArray = inner.readBytes()
}

class DataOutputStream(private val inner: OutputStream) : OutputStream() {
    override fun write(b: Int) = inner.write(b)
    fun writeInt(v: Int) { write(v ushr 24); write(v ushr 16); write(v ushr 8); write(v) }
    fun writeShort(v: Int) { write(v ushr 8); write(v) }
    override fun flush() = inner.flush()
    override fun close() = inner.close()
}
