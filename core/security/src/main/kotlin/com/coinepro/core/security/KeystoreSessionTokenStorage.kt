package com.coinepro.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.coinepro.core.auth.SessionTokenStorage
import com.coinepro.core.model.MarketPlatform
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.secureSessionDataStore by preferencesDataStore(name = "secure_session")

/**
 * Encrypted token storage for one platform.
 *
 * The platform's [MarketPlatform.id] selects the preference key, so CoinePro-FX and TradeYar
 * credentials sit side by side in the same store and clearing one never signs the user out of the
 * other. The AES key in the Android keystore stays shared: it protects the store, and both entries
 * belong to the same person on the same device.
 */
class KeystoreSessionTokenStorage(
    context: Context,
    private val platform: MarketPlatform,
) : SessionTokenStorage {
    private val appContext = context.applicationContext
    private val tokenKey = stringPreferencesKey("session_ciphertext_${platform.id}")
    private val refreshKey = stringPreferencesKey("session_refresh_ciphertext_${platform.id}")

    override suspend fun readToken(): String? = read(tokenKey)

    override suspend fun writeToken(token: String) = write(tokenKey, token)

    override suspend fun readRefreshToken(): String? = read(refreshKey)

    override suspend fun writeRefreshToken(token: String) = write(refreshKey, token)

    /**
     * Both tokens go together. A refresh token left behind after a sign-out is a live credential
     * for an account nobody is signed in to, and it would let the next refresh silently restore a
     * session the reader deliberately ended.
     */
    override suspend fun clear() {
        appContext.secureSessionDataStore.edit {
            it.remove(tokenKey)
            it.remove(refreshKey)
        }
    }

    private suspend fun read(key: Preferences.Key<String>): String? {
        val encoded = appContext.secureSessionDataStore.data.first()[key] ?: return null
        return try {
            decrypt(encoded)
        } catch (_: Exception) {
            appContext.secureSessionDataStore.edit { it.remove(key) }
            null
        }
    }

    private suspend fun write(key: Preferences.Key<String>, token: String) {
        require(token.isNotBlank())
        val encrypted = encrypt(token)
        appContext.secureSessionDataStore.edit { it[key] = encrypted }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, ciphertext)
            .joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "coinepro_session_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
