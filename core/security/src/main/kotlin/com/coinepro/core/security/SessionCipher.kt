package com.coinepro.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Turns a token into something safe to write to disk, and back.
 *
 * An interface with one real implementation, and the seam is not an invention: the Android keystore
 * is a platform service with no local test double — Robolectric does not ship the `AndroidKeyStore`
 * provider — while the envelope around it is ordinary string handling with real failure modes. A
 * store that mangles its own envelope loses a session; one that throws on a mangled one crashes the
 * app on launch. Both are worth a test, and neither needs a keystore.
 */
interface SessionCipher {
    fun encrypt(value: String): String

    /** @throws Exception when the envelope is unreadable — the caller drops the entry. */
    fun decrypt(value: String): String
}

/**
 * AES-GCM under a key held in the Android keystore.
 *
 * The key is shared across platforms on purpose: it protects the store, and both platforms'
 * credentials belong to the same person on the same device. What is *not* shared is the preference
 * key — see [KeystoreSessionTokenStorage].
 */
class KeystoreSessionCipher(
    private val alias: String = DEFAULT_ALIAS,
) : SessionCipher {

    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return envelope(cipher.iv, ciphertext)
    }

    override fun decrypt(value: String): String {
        val (iv, ciphertext) = openEnvelope(value)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        const val DEFAULT_ALIAS = "coinepro_session_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

/**
 * `base64(iv):base64(ciphertext)`.
 *
 * The IV travels with the ciphertext because GCM needs a fresh one per encryption and it is not a
 * secret — reusing one under the same key is the failure that matters, and generating one per call
 * is what the cipher already does. `NO_WRAP` because a newline inside a preference value is a
 * newline the reader has to strip back out, and forgetting to is a decrypt that fails on entries
 * long enough to have wrapped.
 */
internal fun envelope(iv: ByteArray, ciphertext: ByteArray): String =
    Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP)

/**
 * Splits an envelope, or throws.
 *
 * Throwing is the contract: the caller catches, drops the entry and returns null, which signs the
 * reader out rather than crashing them out. A tolerant parser here would be worse — it would hand
 * a cipher some arbitrary bytes and fail one layer further down, where the error says nothing.
 *
 * Limit two on the split, because base64 with `NO_WRAP` cannot contain a colon but a corrupted
 * value can, and a three-part split would silently discard the tail rather than failing.
 */
internal fun openEnvelope(value: String): Pair<ByteArray, ByteArray> {
    val parts = value.split(':', limit = 2)
    require(parts.size == 2) { "session envelope has no separator" }
    require(parts[0].isNotEmpty() && parts[1].isNotEmpty()) { "session envelope has an empty half" }
    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
    val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
    require(iv.isNotEmpty() && ciphertext.isNotEmpty()) { "session envelope decoded to nothing" }
    return iv to ciphertext
}

/**
 * Where one platform's token is filed.
 *
 * Per platform and separated on purpose: CoinePro-FX and TradeYar are separate systems with
 * separate user tables, so clearing one must never sign the reader out of the other — and reading
 * one under the other's key would send a TradeYar token to CoinePro-FX.
 */
internal fun tokenPreferenceName(platformId: String): String = "session_ciphertext_$platformId"

internal fun refreshPreferenceName(platformId: String): String =
    "session_refresh_ciphertext_$platformId"
