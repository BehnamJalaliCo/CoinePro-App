@file:Suppress("unused")

package androidx.exifinterface.media

/**
 * The one EXIF tag the phone reads — orientation — out of a JPEG's APP1 segment, parsed to the
 * TIFF layout (either byte order). Anything else answers the default, as a file without the tag
 * does on the phone.
 */
class ExifInterface(stream: java.io.InputStream) {
    private val orientation: Int = runCatching { readOrientation(stream.readBytes()) }.getOrNull() ?: ORIENTATION_UNDEFINED

    constructor(path: String) : this(java.io.FileInputStream(path))

    fun getAttributeInt(tag: String, defaultValue: Int): Int =
        if (tag == TAG_ORIENTATION && orientation != ORIENTATION_UNDEFINED) orientation else defaultValue

    val rotationDegrees: Int get() = when (orientation) {
        ORIENTATION_ROTATE_90, ORIENTATION_TRANSPOSE -> 90
        ORIENTATION_ROTATE_180, ORIENTATION_FLIP_VERTICAL -> 180
        ORIENTATION_ROTATE_270, ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }
    val isFlipped: Boolean get() = orientation in setOf(ORIENTATION_FLIP_HORIZONTAL, ORIENTATION_FLIP_VERTICAL, ORIENTATION_TRANSPOSE, ORIENTATION_TRANSVERSE)

    fun getAttribute(tag: String): String? = if (tag == TAG_ORIENTATION && orientation != ORIENTATION_UNDEFINED) orientation.toString() else null

    private fun readOrientation(b: ByteArray): Int? {
        if (b.size < 4 || (b[0].toInt() and 0xff) != 0xFF || (b[1].toInt() and 0xff) != 0xD8) return null
        var i = 2
        while (i + 4 <= b.size) {
            if ((b[i].toInt() and 0xff) != 0xFF) return null
            val marker = b[i + 1].toInt() and 0xff
            val length = ((b[i + 2].toInt() and 0xff) shl 8) or (b[i + 3].toInt() and 0xff)
            if (marker == 0xE1 && i + 10 < b.size && b.decodeToString(i + 4, i + 8) == "Exif") return tiff(b, i + 10)
            if (marker == 0xDA) return null
            i += 2 + length
        }
        return null
    }

    private fun tiff(b: ByteArray, base: Int): Int? {
        val little = b[base].toInt() == 'I'.code
        fun u16(at: Int) = if (little) (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8)
        else ((b[at].toInt() and 0xff) shl 8) or (b[at + 1].toInt() and 0xff)
        fun u32(at: Int) = if (little) u16(at) or (u16(at + 2) shl 16) else (u16(at) shl 16) or u16(at + 2)
        val ifd = base + u32(base + 4)
        val count = u16(ifd)
        for (k in 0 until count) {
            val entry = ifd + 2 + k * 12
            if (entry + 12 > b.size) return null
            if (u16(entry) == 0x0112) return u16(entry + 8)
        }
        return null
    }

    companion object {
        const val TAG_ORIENTATION = "Orientation"
        const val ORIENTATION_UNDEFINED = 0
        const val ORIENTATION_NORMAL = 1
        const val ORIENTATION_FLIP_HORIZONTAL = 2
        const val ORIENTATION_ROTATE_180 = 3
        const val ORIENTATION_FLIP_VERTICAL = 4
        const val ORIENTATION_TRANSPOSE = 5
        const val ORIENTATION_ROTATE_90 = 6
        const val ORIENTATION_TRANSVERSE = 7
        const val ORIENTATION_ROTATE_270 = 8
    }
}
