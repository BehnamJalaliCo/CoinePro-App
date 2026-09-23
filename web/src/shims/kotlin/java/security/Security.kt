@file:Suppress("unused", "UNUSED_PARAMETER")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package java.security

import com.coinepro.web.jvm.Sha256

open class GeneralSecurityException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
class NoSuchAlgorithmException(message: String? = null) : GeneralSecurityException(message)
open class InvalidKeyException(message: String? = null) : GeneralSecurityException(message)
class KeyStoreException(message: String? = null) : GeneralSecurityException(message)
class UnrecoverableKeyException(message: String? = null) : GeneralSecurityException(message)

interface Key {
    val algorithm: String
    val encoded: ByteArray?
    val format: String? get() = "RAW"
}

class MessageDigest private constructor(val algorithm: String) {
    private val buffer = java.io.ByteArrayOutputStream()
    fun update(input: ByteArray) = buffer.write(input)
    fun update(input: Byte) = buffer.write(input.toInt())
    fun update(input: ByteArray, offset: Int, len: Int) = buffer.write(input, offset, len)
    fun digest(): ByteArray = Sha256.digest(buffer.toByteArray()).also { buffer.reset() }
    fun digest(input: ByteArray): ByteArray { update(input); return digest() }
    fun reset() = buffer.reset()
    val digestLength: Int get() = 32

    companion object {
        fun getInstance(algorithm: String): MessageDigest {
            if (algorithm.uppercase().replace("-", "") != "SHA256") throw NoSuchAlgorithmException("$algorithm MessageDigest not available")
            return MessageDigest(algorithm)
        }
        fun isEqual(a: ByteArray?, b: ByteArray?): Boolean {
            if (a === b) return true
            if (a == null || b == null || a.size != b.size) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
            return diff == 0
        }
    }
}

open class SecureRandom {
    fun nextBytes(bytes: ByteArray) { com.coinepro.web.jvm.secureRandomBytes(bytes.size).copyInto(bytes) }
    fun nextInt(): Int { val b = com.coinepro.web.jvm.secureRandomBytes(4); return (b[0].toInt() shl 24) or (b[1].toInt() and 0xff shl 16) or (b[2].toInt() and 0xff shl 8) or (b[3].toInt() and 0xff) }
    fun nextInt(bound: Int): Int = ((nextInt().toLong() and 0xffffffffL) % bound).toInt()
    fun nextLong(): Long = (nextInt().toLong() shl 32) or (nextInt().toLong() and 0xffffffffL)
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()
    fun nextBoolean(): Boolean = nextInt() and 1 == 1
    companion object { fun getInstanceStrong(): SecureRandom = SecureRandom() }
}

private fun keyGetJs(key: String): String? = js("(function () { try { return localStorage.getItem(key); } catch (e) { return null; } })()")
private fun keySetJs(key: String, value: String): Unit = js("(function () { try { localStorage.setItem(key, value); } catch (e) {} })()")
private fun keyRemoveJs(key: String): Unit = js("(function () { try { localStorage.removeItem(key); } catch (e) {} })()")

/**
 * "AndroidKeyStore", for a page: the key is generated in the page and kept in the page's storage.
 * A browser has no hardware-backed keystore a page can reach synchronously; what this keeps is the
 * phone's envelope format, so a session stored by the page reads back in it.
 */
class KeyStore private constructor(val type: String) {
    fun load(param: Any?) {}
    fun getKey(alias: String, password: CharArray?): Key? =
        keyGetJs("keystore:$alias")?.let { javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode(it), "AES") }
    fun containsAlias(alias: String): Boolean = keyGetJs("keystore:$alias") != null
    fun deleteEntry(alias: String) = keyRemoveJs("keystore:$alias")
    fun aliases(): java.util.Enumeration<String> = java.util.Enumeration(emptyList())

    companion object {
        fun getInstance(type: String): KeyStore = KeyStore(type)
        internal fun store(alias: String, key: ByteArray) = keySetJs("keystore:$alias", java.util.Base64.getEncoder().encodeToString(key))
    }
}
