package com.coinepro.app.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.core.auth.PendingRegistration
import com.coinepro.core.auth.RegistrationMemory
import kotlinx.coroutines.flow.first

/**
 * A half-finished registration, kept where a killed process cannot take it.
 *
 * In the ordinary preferences file rather than the encrypted store, deliberately. What is written
 * here is a registration token and the address it was issued for: the token names an *unfinished*
 * sign-up, is useless without the code that only reaches the reader's inbox, and is spent the
 * moment it is used. Treating it as a credential would mean paying the keystore's cost for
 * something that cannot sign anybody in.
 *
 * The password is not stored, here or anywhere. It went to the server with the first step and the
 * app has no reason to hold it — which is also why "resend the code" is "start again" rather than
 * a button, as the controller explains.
 */
class RegistrationStore(private val dataStore: DataStore<Preferences>) : RegistrationMemory {

    override suspend fun save(pending: PendingRegistration?) {
        dataStore.edit { preferences ->
            if (pending == null) {
                preferences.remove(TOKEN)
                preferences.remove(EMAIL)
            } else {
                preferences[TOKEN] = pending.registrationToken
                preferences[EMAIL] = pending.email
            }
        }
    }

    override suspend fun load(): PendingRegistration? {
        val preferences = dataStore.data.first()
        val token = preferences[TOKEN]?.takeIf(String::isNotBlank) ?: return null
        val email = preferences[EMAIL]?.takeIf(String::isNotBlank) ?: return null
        return PendingRegistration(token, email)
    }

    private companion object {
        val TOKEN = stringPreferencesKey("registration_token")
        val EMAIL = stringPreferencesKey("registration_email")
    }
}
