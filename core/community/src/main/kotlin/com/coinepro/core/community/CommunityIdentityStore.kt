package com.coinepro.core.community

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Who this install is on the board.
 *
 * ### The whole identity model, in two values
 *
 * A **key** and a **name**. The key is a random secret this app mints once and sends on every
 * community request as `X-Community-Key`; the server stores only its hash. The name is what the
 * reader chose to be called, and it is the only thing anybody else ever sees. There is no account
 * behind either — no phone, no email, no session on CoinePro-FX or TradeYar — which is the owner's
 * instruction («کامیونیتی باید مستقل از کوین پرو اف ایکس و تریدیار باشه») expressed as a store with
 * two fields.
 *
 * ### Why the key is minted here and not returned by the server
 *
 * Because a secret that never travels to the phone in a response body cannot be replayed from a
 * log. The server learns it only as a header and keeps only a digest, so the one place the plain
 * value exists is this store, on this device. Uninstalling the app ends it, and with it the name:
 * that is the intended trade for a board with no sign-up, and it is why there is nothing here
 * worth stealing.
 *
 * ### What the name here is, and is not
 *
 * A **cache** of the server's answer, not the truth. The truth is the row the server holds against
 * the key; this copy exists so the composer knows whether to ask for a name before the first
 * request rather than after it. The server's `401` is still handled — see
 * [CommunityController] — for the day the two disagree.
 */
interface CommunityIdentityStore {
    /** The secret that names this install to the board. Minted on first use, never rotated. */
    suspend fun key(): String

    /** The display name the server confirmed, or null before the reader has chosen one. */
    val displayName: Flow<String?>

    /** Records the name the server confirmed — or forgets it, on a refusal that says it is gone. */
    suspend fun setDisplayName(name: String?)
}

/**
 * The store on the app's preferences.
 *
 * The key is created inside a single [edit] so two callers racing at startup cannot mint two keys
 * and have one silently win — the app would then present a different identity on the next launch
 * and the reader's name would be gone. The same shape `InstallIdStore` uses, for the same reason.
 */
class PreferencesCommunityIdentityStore(
    private val dataStore: DataStore<Preferences>,
) : CommunityIdentityStore {

    override suspend fun key(): String {
        dataStore.data.first()[KEY]?.takeIf { it.isNotBlank() }?.let { return it }
        return dataStore.edit { preferences ->
            if (preferences[KEY].isNullOrBlank()) preferences[KEY] = mint()
        }[KEY]!!
    }

    override val displayName: Flow<String?> = dataStore.data.map { it[NAME]?.takeIf(String::isNotBlank) }

    override suspend fun setDisplayName(name: String?) {
        dataStore.edit { preferences ->
            if (name.isNullOrBlank()) preferences.remove(NAME) else preferences[NAME] = name
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("community_key")
        val NAME = stringPreferencesKey("community_display_name")

        /** Thirty-two random bytes as hex: sixty-four characters, inside the server's 16–128 window. */
        const val KEY_BYTES = 32
        private val RANDOM = SecureRandom()

        fun mint(): String {
            val bytes = ByteArray(KEY_BYTES).also(RANDOM::nextBytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
