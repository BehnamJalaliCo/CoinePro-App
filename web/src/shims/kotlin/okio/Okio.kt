package okio

class ByteString(private val data: ByteArray) {
    fun utf8(): String = data.decodeToString()
    fun toByteArray(): ByteArray = data.copyOf()
    val size: Int get() = data.size
    fun hex(): String = data.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    override fun toString(): String = "[size=${data.size}]"
    companion object {
        fun String.encodeUtf8(): ByteString = ByteString(encodeToByteArray())
        fun ByteArray.toByteString(): ByteString = ByteString(copyOf())
        fun of(vararg data: Byte): ByteString = ByteString(data)
        val EMPTY = ByteString(ByteArray(0))
    }
}
