package javax.crypto.spec

class SecretKeySpec(private val key: ByteArray, override val algorithm: String) : javax.crypto.SecretKey {
    override val encoded: ByteArray get() = key.copyOf()
}

class GCMParameterSpec(val tLen: Int, val iv: ByteArray)

class IvParameterSpec(val iv: ByteArray)
