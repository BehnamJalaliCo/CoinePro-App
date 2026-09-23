@file:Suppress("unused")

package java.util.zip

import java.io.OutputStream

/*
 * A zip writer — enough for the workbook export, which is a zip of XML parts. Entries are stored
 * rather than deflated: a page has no zlib it can call synchronously, and a stored entry is a valid
 * zip entry every spreadsheet opens. The files are a little larger; nothing else differs.
 */

class CRC32 {
    private var crc = 0xffffffffL
    fun update(b: Int) {
        var c = (crc xor (b.toLong() and 0xff)) and 0xffffffffL
        repeat(8) { c = if (c and 1L != 0L) (c ushr 1) xor 0xedb88320L else c ushr 1 }
        crc = c
    }
    fun update(bytes: ByteArray) = bytes.forEach { update(it.toInt()) }
    fun update(bytes: ByteArray, off: Int, len: Int) { for (i in off until off + len) update(bytes[i].toInt()) }
    val value: Long get() = crc xor 0xffffffffL
    fun reset() { crc = 0xffffffffL }
}

class ZipEntry(val name: String) {
    var time: Long = 0
    var size: Long = -1
    var method: Int = STORED
    companion object {
        const val STORED = 0
        const val DEFLATED = 8
    }
}

class ZipException(message: String?) : java.io.IOException(message)

class ZipOutputStream(private val out: OutputStream) : OutputStream() {
    private class Written(val name: ByteArray, val crc: Long, val size: Int, val offset: Int)

    private val written = ArrayList<Written>()
    private var current: ZipEntry? = null
    private var buffer = java.io.ByteArrayOutputStream()
    private var offset = 0
    private var finished = false

    fun setLevel(level: Int) {}
    fun setMethod(method: Int) {}
    fun setComment(comment: String?) {}

    fun putNextEntry(entry: ZipEntry) {
        if (current != null) closeEntry()
        current = entry
        buffer = java.io.ByteArrayOutputStream()
    }

    override fun write(b: Int) { buffer.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) { buffer.write(b, off, len) }
    override fun write(b: ByteArray) = write(b, 0, b.size)

    fun closeEntry() {
        val entry = current ?: return
        val data = buffer.toByteArray()
        val crc = CRC32().also { it.update(data) }.value
        val name = entry.name.encodeToByteArray()
        val header = java.io.ByteArrayOutputStream()
        header.le32(0x04034b50); header.le16(20); header.le16(0x0800); header.le16(0)
        header.le16(0); header.le16(0x21)
        header.le32(crc.toInt()); header.le32(data.size); header.le32(data.size)
        header.le16(name.size); header.le16(0)
        header.write(name)
        val bytes = header.toByteArray()
        out.write(bytes); out.write(data)
        written += Written(name, crc, data.size, offset)
        offset += bytes.size + data.size
        current = null
    }

    fun finish() {
        if (finished) return
        if (current != null) closeEntry()
        val directory = java.io.ByteArrayOutputStream()
        written.forEach { w ->
            directory.le32(0x02014b50); directory.le16(20); directory.le16(20); directory.le16(0x0800); directory.le16(0)
            directory.le16(0); directory.le16(0x21)
            directory.le32(w.crc.toInt()); directory.le32(w.size); directory.le32(w.size)
            directory.le16(w.name.size); directory.le16(0); directory.le16(0); directory.le16(0); directory.le16(0)
            directory.le32(0); directory.le32(w.offset)
            directory.write(w.name)
        }
        val dir = directory.toByteArray()
        out.write(dir)
        val end = java.io.ByteArrayOutputStream()
        end.le32(0x06054b50); end.le16(0); end.le16(0); end.le16(written.size); end.le16(written.size)
        end.le32(dir.size); end.le32(offset); end.le16(0)
        out.write(end.toByteArray())
        finished = true
    }

    override fun flush() = out.flush()
    override fun close() { finish(); out.close() }

    private fun java.io.ByteArrayOutputStream.le16(v: Int) { write(v and 0xff); write((v ushr 8) and 0xff) }
    private fun java.io.ByteArrayOutputStream.le32(v: Int) { le16(v and 0xffff); le16((v ushr 16) and 0xffff) }
}
