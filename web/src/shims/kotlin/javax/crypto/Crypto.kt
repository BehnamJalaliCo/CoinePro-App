@file:Suppress("unused", "UNUSED_PARAMETER")

package javax.crypto

import com.coinepro.web.jvm.AesGcm
import com.coinepro.web.jvm.hmacSha256
import com.coinepro.web.jvm.secureRandomBytes
import java.security.Key

interface SecretKey : Key

class AEADBadTagException(message: String? = null) : BadPaddingException(message)
open class BadPaddingException(message: String? = null) : java.security.GeneralSecurityException(message)
class IllegalBlockSizeException(message: String? = null) : java.security.GeneralSecurityException(message)
class NoSuchPaddingException(message: String? = null) : java.security.GeneralSecurityException(message)

class Mac private constructor(val algorithm: String) {
    private var key: ByteArray? = null
    private val buffer = java.io.ByteArrayOutputStream()
    val macLength: Int get() = 32
    fun init(key: Key) { this.key = key.encoded ?: throw java.security.InvalidKeyException("No key bytes") }
    fun update(input: ByteArray) = buffer.write(input)
    fun update(input: Byte) = buffer.write(input.toInt())
    fun doFinal(): ByteArray = hmacSha256(key ?: throw IllegalStateException("MAC not initialized"), buffer.toByteArray()).also { buffer.reset() }
    fun doFinal(input: ByteArray): ByteArray { update(input); return doFinal() }
    fun reset() = buffer.reset()
    companion object {
        fun getInstance(algorithm: String): Mac {
            if (!algorithm.equals("HmacSHA256", ignoreCase = true)) throw java.security.NoSuchAlgorithmException("Algorithm $algorithm not available")
            return Mac(algorithm)
        }
    }
}

class Cipher private constructor(val transformation: String) {
    private var mode = 0
    private var key: ByteArray? = null
    private var tagBits = 128
    var iv: ByteArray = ByteArray(0)
        private set
    val blockSize: Int get() = 16

    fun init(mode: Int, key: Key) {
        this.mode = mode
        this.key = key.encoded
        if (mode == ENCRYPT_MODE) iv = secureRandomBytes(12)
    }

    fun init(mode: Int, key: Key, spec: Any?) {
        init(mode, key)
        when (spec) {
            is javax.crypto.spec.GCMParameterSpec -> { iv = spec.iv; tagBits = spec.tLen }
            is javax.crypto.spec.IvParameterSpec -> iv = spec.iv
        }
    }

    fun doFinal(input: ByteArray): ByteArray {
        val k = key ?: throw IllegalStateException("Cipher not initialized")
        return when (mode) {
            ENCRYPT_MODE -> AesGcm.encrypt(k, iv, input, tagBits)
            DECRYPT_MODE -> AesGcm.decrypt(k, iv, input, tagBits)
            else -> throw IllegalStateException("Cipher not initialized")
        }
    }

    companion object {
        const val ENCRYPT_MODE = 1
        const val DECRYPT_MODE = 2
        fun getInstance(transformation: String): Cipher {
            if (!transformation.startsWith("AES/GCM", ignoreCase = true)) throw java.security.NoSuchAlgorithmException("Cannot find any provider supporting $transformation")
            return Cipher(transformation)
        }
    }
}

class KeyGenerator private constructor(val algorithm: String) {
    private var alias: String? = null
    private var size = 256
    fun init(spec: Any?) {
        if (spec is android.security.keystore.KeyGenParameterSpec) alias = spec.keystoreAlias
    }
    fun init(keySize: Int) { size = keySize }
    fun generateKey(): SecretKey {
        val bytes = secureRandomBytes(size / 8)
        alias?.let { java.security.KeyStore.store(it, bytes) }
        return javax.crypto.spec.SecretKeySpec(bytes, algorithm)
    }
    companion object {
        fun getInstance(algorithm: String): KeyGenerator = KeyGenerator(algorithm)
        fun getInstance(algorithm: String, provider: String): KeyGenerator = KeyGenerator(algorithm)
    }
}
