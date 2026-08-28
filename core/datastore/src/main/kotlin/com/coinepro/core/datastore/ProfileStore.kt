package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.core.model.AvatarSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A reader's own name and face, as this device holds them. */
data class StoredProfile(
    /**
     * What the reader typed, or null if they never did.
     *
     * Null is not the same as empty and the difference is the whole reason this is nullable: null
     * means "use the name the server knows", empty would mean "this reader chose to be called
     * nothing", which is not a choice the screen offers.
     */
    val displayName: String? = null,
    val avatar: AvatarSpec = AvatarSpec.Default,
    /** A short line the reader wrote about themselves. Null until they write one. */
    val tagline: String? = null,
    /**
     * Whether the reader has asked for their money not to be drawn on screen.
     *
     * Every serious finance application has this and the reason is not squeamishness — it is that
     * people open these apps on a bus, at a desk, in front of a camera. The reader who taps it is
     * not hiding the figure from themselves; they are hiding it from whoever is over their
     * shoulder, and the app is the only thing that can help with that.
     *
     * It is remembered rather than reset each launch, because somebody who wants their balance
     * hidden wants it hidden the *next* time they open the app in the same room. The default is
     * off: a first-run reader who cannot see their own balance has been given a bug, not a
     * feature.
     */
    val balanceHidden: Boolean = false,
)

/**
 * Where the profile lives, which is here and nowhere else.
 *
 * **Nothing in this store is sent anywhere.** Neither backend has a route for an avatar, and this
 * app is not going to invent one by uploading a reader's photograph to a trading server. A profile
 * picture is a local preference in the same category as a chosen theme; treating it as account data
 * would mean a privacy policy paragraph, an export obligation and a deletion path, all bought for a
 * decoration.
 *
 * That has one consequence worth stating plainly rather than discovering: **a reinstall loses it**,
 * and so does a second device. The screen says so.
 *
 * The display name is an *override*. The signed-in name comes from the server and is usually right;
 * this is for the reader who wants to be called something else, and a null here means "the server's
 * answer is fine" rather than "no name".
 */
class ProfileStore(private val dataStore: DataStore<Preferences>) {

    val profile: Flow<StoredProfile> = dataStore.data.map { preferences ->
        StoredProfile(
            displayName = preferences[DISPLAY_NAME]?.takeIf(String::isNotBlank),
            avatar = AvatarSpec.decode(preferences[AVATAR]),
            tagline = preferences[TAGLINE]?.takeIf(String::isNotBlank),
            balanceHidden = preferences[BALANCE_HIDDEN] == true,
        )
    }

    suspend fun setDisplayName(name: String?) {
        dataStore.edit { preferences ->
            val trimmed = name?.trim().orEmpty().take(MAX_NAME)
            if (trimmed.isEmpty()) preferences.remove(DISPLAY_NAME) else preferences[DISPLAY_NAME] = trimmed
        }
    }

    suspend fun setTagline(tagline: String?) {
        dataStore.edit { preferences ->
            val trimmed = tagline?.trim().orEmpty().take(MAX_TAGLINE)
            if (trimmed.isEmpty()) preferences.remove(TAGLINE) else preferences[TAGLINE] = trimmed
        }
    }

    suspend fun setBalanceHidden(hidden: Boolean) {
        dataStore.edit { preferences -> preferences[BALANCE_HIDDEN] = hidden }
    }

    suspend fun setAvatar(spec: AvatarSpec) {
        dataStore.edit { preferences -> preferences[AVATAR] = AvatarSpec.encode(spec) }
    }

    /**
     * Forgets the reader entirely.
     *
     * Called on sign-out, because the next person to open this app on this phone is not necessarily
     * the same person, and a stranger's photograph over a stranger's name is the loudest possible
     * way to tell them the sign-out did not work.
     */
    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(DISPLAY_NAME)
            preferences.remove(TAGLINE)
            preferences.remove(AVATAR)
            // The privacy choice goes too. It is a statement about one person's circumstances, and
            // leaving it set would tell the next reader something about the last one.
            preferences.remove(BALANCE_HIDDEN)
        }
    }

    private companion object {
        val DISPLAY_NAME = stringPreferencesKey("profile_display_name")
        val TAGLINE = stringPreferencesKey("profile_tagline")
        val AVATAR = stringPreferencesKey("profile_avatar")
        val BALANCE_HIDDEN = booleanPreferencesKey("profile_balance_hidden")

        /** Enough for a real name in either script, short enough to fit a row without eliding. */
        const val MAX_NAME = 40
        const val MAX_TAGLINE = 80
    }
}
