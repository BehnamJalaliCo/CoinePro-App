package com.coinepro.core.security

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.coinepro.core.auth.SessionTokenStorage
import com.coinepro.core.model.MarketPlatform
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
    /**
     * Injected rather than constructed here, because the Android keystore has no local test
     * double — Robolectric does not ship the provider — and everything around it does.
     */
    private val cipher: SessionCipher = KeystoreSessionCipher(),
) : SessionTokenStorage {
    private val appContext = context.applicationContext
    private val tokenKey = stringPreferencesKey(tokenPreferenceName(platform.id))
    private val refreshKey = stringPreferencesKey(refreshPreferenceName(platform.id))

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

    /**
     * An unreadable entry is dropped rather than propagated.
     *
     * The cases are real: a keystore key invalidated by a lock-screen change, a partial write, a
     * restored backup from another device. All of them mean the same thing to a reader — sign in
     * again — and none of them should be a crash on launch. Removing the entry also stops the app
     * retrying the same broken value on every start.
     */
    private suspend fun read(key: Preferences.Key<String>): String? {
        val encoded = appContext.secureSessionDataStore.data.first()[key] ?: return null
        return try {
            cipher.decrypt(encoded)
        } catch (_: Exception) {
            appContext.secureSessionDataStore.edit { it.remove(key) }
            null
        }
    }

    private suspend fun write(key: Preferences.Key<String>, token: String) {
        require(token.isNotBlank())
        val encrypted = cipher.encrypt(token)
        appContext.secureSessionDataStore.edit { it[key] = encrypted }
    }
}
