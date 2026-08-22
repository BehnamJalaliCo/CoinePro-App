package com.coinepro.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.coinepro.core.auth.SessionTokenStorage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.secureSessionDataStore by preferencesDataStore(name = "secure_session")

class KeystoreSessionTokenStorage(
    context: Context,
) : SessionTokenStorage {
    private val appContext = context.applicationContext

    override suspend fun readToken(): String? {
        val encoded = appContext.secureSessionDataStore.data.first()[TOKEN] ?: return null
        return try {
            decrypt(encoded)
        } catch (_: Exception) {
            appContext.secureSessionDataStore.edit { it.remove(TOKEN) }
            null
        }
    }

    override suspend fun writeToken(token: String) {
        require(token.isNotBlank())
        val encrypted = encrypt(token)
        appContext.secureSessionDataStore.edit { it[TOKEN] = encrypted }
    }

    override suspend fun clear() {
        appContext.secureSessionDataStore.edit { it.remove(TOKEN) }
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
        val TOKEN = stringPreferencesKey("session_ciphertext")
        const val KEY_ALIAS = "coinepro_session_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
