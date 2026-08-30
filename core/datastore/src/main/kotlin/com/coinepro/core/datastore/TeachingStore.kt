package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which teaching banners the reader has already read and put away.
 *
 * ### Why dismissal has to be on disk
 *
 * A teaching banner explains what a screen is for. It is worth one reading, and after that it is a
 * strip of text between the reader and the thing they came for. A banner that comes back after it
 * was dismissed is worse than no banner at all: the first one costs a person three seconds, the
 * second one teaches them that the dismiss control is a lie, and from then on they stop reading
 * anything the app puts in front of them — including the sentences that matter. So the dismissal is
 * permanent, it is per screen, and it survives the process. That is this whole file.
 *
 * ### One row for every screen, not one row each
 *
 * The alternative — a boolean preference per surface — is twenty-odd keys that have to be declared
 * somewhere, and adding a screen means adding a key. What is stored here is the *set of surfaces
 * that were dismissed*, so a screen that has never been dismissed occupies nothing, and a screen
 * that no longer exists is a token that is simply never asked about again. Adding a surface needs
 * no change on this side at all.
 *
 * ### The keys are opaque to this module
 *
 * `core:datastore` does not know what a screen is and must not: the catalogue of surfaces and their
 * copy lives in `core:designsystem`, next to the component that draws them, and this module cannot
 * see it — nor should it, because that direction would put Compose in a preferences module. What
 * crosses the boundary is a short lowercase token, `markets` or `paper_trade`, and [usable] is the
 * one rule about it. Both sides agree on the shape and neither owns the other.
 *
 * That opacity buys one more thing. **A key may carry a revision** — `markets` today, `markets.2`
 * if the sentence is ever rewritten into something materially different — and bumping it brings the
 * banner back for everybody exactly once, because nobody has dismissed the new token. The old token
 * stays on disk, harmless, and ages out under [MAX_SURFACES]. That is deliberate: it is the only
 * honest way to re-teach a screen whose explanation turned out to be wrong, and it costs one
 * character.
 *
 * ### Reading never throws, and never invents a dismissal
 *
 * An absent entry, a blank one, a half-written one, a token from a build this one predates: all of
 * them read as "nothing dismissed". The bias is deliberate and it only goes one way. Guessing
 * *dismissed* over a storage accident would silently delete the teaching for a reader who never
 * dismissed anything, and they would never find out there had been an explanation; guessing *not
 * dismissed* shows one sentence one extra time. Those are not comparable costs.
 *
 * ### The encoding
 *
 * The delimited-string scheme every other store in this package uses — ASCII's group separator
 * between tokens — for the same reason: the alternative is a serialisation library in a preferences
 * module. A token cannot contain the separator because [usable] restricts what a token may be, so
 * decoding cannot produce something that was never written.
 */
class TeachingStore(private val dataStore: DataStore<Preferences>) {

    /**
     * The surfaces whose banner has been put away.
     *
     * A set: order carries nothing a reader can see. Distinct-until-changed because the host
     * collects this once for the whole app and every unrelated preference write lands on the same
     * `DataStore`; without it, changing the theme would recompose every teaching banner on screen.
     *
     * The first emission is what the host waits for before it draws any banner at all — see
     * `TeachingDismissals.ready` on the other side. A screen that renders its banner against an
     * assumed-empty set and then hides it a frame later is a flash, which is precisely the sort of
     * thing this mechanism exists not to do.
     */
    fun dismissed(): Flow<Set<String>> = dataStore.data
        .map { preferences -> decode(preferences[DISMISSED]) }
        .distinctUntilChanged()

    /**
     * Records that the reader has read this screen's banner and does not want it again.
     *
     * Idempotent, and it does not rewrite the row when the token is already there — a reader who
     * double-taps should not cost a disk write, and more importantly should not reorder the set and
     * push somebody else's older token toward the cap.
     */
    suspend fun dismiss(surface: String) {
        val clean = usable(surface) ?: return
        dataStore.edit { preferences ->
            val current = decode(preferences[DISMISSED])
            if (clean in current) return@edit
            preferences[DISMISSED] = encode(current + clean)
        }
    }

    /**
     * Puts one screen's banner back.
     *
     * This is the half that makes dismissing safe. Dismissal is permanent, which is only defensible
     * because it is also reversible from the screen itself — the «؟» in the header calls this — so
     * a reader who put the explanation away in their first week can have it back in their second
     * without hunting through settings for a "reset all tips" switch nobody finds.
     *
     * Restoring the last dismissal removes the entry rather than storing an empty string, so a
     * reader who undoes everything leaves nothing behind for a later version to have to parse.
     */
    suspend fun restore(surface: String) {
        val clean = usable(surface) ?: return
        dataStore.edit { preferences ->
            val current = decode(preferences[DISMISSED])
            if (clean !in current) return@edit
            write(preferences, current - clean)
        }
    }

    /** Every banner back, for the "show the teaching again" switch in settings. */
    suspend fun restoreAll() {
        dataStore.edit { preferences -> preferences.remove(DISMISSED) }
    }

    companion object {
        internal val DISMISSED = stringPreferencesKey("teaching_dismissed")

        /**
         * How many dismissals are kept.
         *
         * Well past the two dozen surfaces the app has, because revised keys accumulate beside the
         * ones they replace — see the class note. It is a bound on a runaway writer rather than a
         * limit anybody meets, the same job [IntervalFavouritesStore.MAX_FAVOURITES] does.
         *
         * When it is reached the **oldest** tokens go, not the newest. That is the opposite of the
         * favourites cap and it is the right way round here: the newest dismissal is the one the
         * reader just made, and dropping it would make the banner they just closed reappear.
         */
        const val MAX_SURFACES = 128

        /** How long a surface key may be. `economic_calendar` is 17 characters. */
        internal const val MAX_KEY_LENGTH = 48

        /** Between tokens. ASCII group separator. */
        private const val GROUP = "\u001D"

        private fun write(preferences: MutablePreferences, keys: Set<String>) {
            if (keys.isEmpty()) {
                preferences.remove(DISMISSED)
            } else {
                preferences[DISMISSED] = encode(keys)
            }
        }

        /**
         * Splits a stored row, dropping anything that cannot be a key and keeping everything that
         * can.
         *
         * Insertion order is preserved — a `LinkedHashSet` — because [encode]'s cap trims from the
         * front, and "oldest first" is only meaningful if the order survives a round trip.
         */
        internal fun decode(stored: String?): Set<String> {
            if (stored.isNullOrBlank()) return emptySet()
            return stored.split(GROUP).mapNotNullTo(LinkedHashSet(), ::usable)
        }

        /** Writes the row, keeping the most recent [MAX_SURFACES] tokens. */
        internal fun encode(keys: Collection<String>): String =
            keys.toList().takeLast(MAX_SURFACES).joinToString(GROUP)

        /**
         * One surface key, normalised, or null if it cannot be one.
         *
         * Lowercased so a caller's `Markets` and the catalogue's `markets` are one dismissal rather
         * than two. The character set is deliberately narrow — lowercase letters, digits,
         * underscore, hyphen and the dot that carries a revision — because that is what makes a key
         * unable to contain the separator, which is what makes decoding total.
         */
        internal fun usable(surface: String?): String? {
            val clean = surface?.trim()?.lowercase() ?: return null
            if (clean.isEmpty() || clean.length > MAX_KEY_LENGTH) return null
            if (clean.any { it !in 'a'..'z' && it !in '0'..'9' && it != '_' && it != '-' && it != '.' }) {
                return null
            }
            return clean
        }
    }
}
