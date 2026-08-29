package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which kinds of event get a glyph on the chart's time axis.
 *
 * ### Why this is a setting at all
 *
 * The axis has room for one row of marks. There are five kinds of thing that want to sit in it —
 * a news headline, an economic release, an earnings date, a dividend, a split — and turning all
 * five on at once does not produce a rich chart, it produces a smear: a solid line of overlapping
 * glyphs under the price, none of them readable and none of them tappable. Every terminal that
 * draws events on the axis ships this switch for that reason.
 *
 * ### News on, the rest off, and that is not a coin toss
 *
 * A headline is the only one of the five that can move a price on any instrument at any minute,
 * which is what an axis mark is for. Earnings, dividends and splits are equities events and are
 * simply not present on most of what this app's readers watch; an economic calendar is dense enough
 * that on an intraday chart it is the smear all by itself. So the default is the one kind that
 * pays for its space, and a reader who wants the others will find the setting — the reverse
 * mistake, shipping all five on, is one they meet as a broken chart rather than as a preference.
 *
 * ### Every kind is a plain String
 *
 * The same rule the rest of this package follows: `core:datastore` must not depend on the module
 * that fetches or draws events, so a kind is an id and not an enum. [ALL_KINDS] is what this build
 * knows about, but a kind read off disk that is **not** in it is kept and handed back rather than
 * dropped — that is a newer build's sixth kind, and a reader who downgrades for a day should not
 * come back to find the setting silently thrown away. The caller draws the kinds it understands and
 * ignores the rest.
 *
 * ### Stored-empty and never-stored both mean "the default". Explicitly-empty does not.
 *
 * The same three-state problem [IntervalFavouritesStore] documents at length, and the same answer.
 * Nothing written, or a blank or unreadable row, is a reader who has expressed no opinion and gets
 * [DEFAULT_KINDS]. A reader who switched every kind off, one at a time, has expressed one, and it
 * is stored as [EMPTY_SELECTION] so it survives — otherwise news would quietly come back on at the
 * next launch and the switch would look broken.
 *
 * ### The encoding
 *
 * The delimited-string scheme the rest of this package uses. One separator is enough — ASCII's
 * group separator between kinds — because a kind has no fields. Decoding never throws: a token
 * that cannot be a kind is dropped on its own and the kinds around it are kept.
 */
class ChartEventPrefsStore(private val dataStore: DataStore<Preferences>) {

    /**
     * The event kinds to draw, as a set because order means nothing on an axis.
     *
     * Distinct-until-changed because the chart collects this for the life of the screen while
     * unrelated preferences are written next to it, and rebuilding the axis marks because somebody
     * starred a symbol would be a visible stutter.
     */
    fun kinds(): Flow<Set<String>> = dataStore.data
        .map { preferences -> read(preferences[KINDS]) }
        .distinctUntilChanged()

    /**
     * Switches one kind on or off, leaving the others alone.
     *
     * The first call materialises [DEFAULT_KINDS] and edits those, rather than writing a set of
     * one: a reader who switches earnings on is adding it to the news marks they can already see,
     * and dropping those would look like the app had eaten them. Switching the last kind off writes
     * [EMPTY_SELECTION] — see the class note for why that is not an empty string.
     */
    suspend fun setKind(kind: String, on: Boolean) {
        val clean = usable(kind) ?: return
        dataStore.edit { preferences ->
            val current = read(preferences[KINDS])
            val next = if (on) current + clean else current - clean
            if (next == current) return@edit
            preferences[KINDS] = write(next)
        }
    }

    companion object {
        internal val KINDS = stringPreferencesKey("chart_event_kinds")

        /** A headline. The one kind that can move any instrument at any minute. */
        const val KIND_NEWS = "news"

        /** A scheduled macroeconomic release — a rate decision, an inflation print. */
        const val KIND_ECONOMIC = "economic"

        /** An equity's results date. */
        const val KIND_EARNINGS = "earnings"

        /** An equity's dividend, on its ex-date. */
        const val KIND_DIVIDEND = "dividend"

        /** A share split or reverse split, which is also where an unadjusted series jumps. */
        const val KIND_SPLIT = "split"

        /**
         * Every kind this build draws, in the order a settings screen should list them.
         *
         * Not a validation list. A kind stored by a newer build is honoured even though it is not
         * here — see the class note — so this is what the app can *offer*, not what it will accept.
         */
        val ALL_KINDS: List<String> =
            listOf(KIND_NEWS, KIND_ECONOMIC, KIND_EARNINGS, KIND_DIVIDEND, KIND_SPLIT)

        /** News alone. See the class note for why the other four start off. */
        val DEFAULT_KINDS: Set<String> = setOf(KIND_NEWS)

        /**
         * What "the reader switched every kind off" is written as.
         *
         * A hyphen, and it is safe because [usable] admits only lowercase letters and underscores,
         * so no kind can ever be spelled this way and no round trip can turn a real kind into this
         * token. Anything added to this file later has to keep that true.
         */
        internal const val EMPTY_SELECTION = "-"

        /** Between kinds. ASCII group separator. */
        private const val GROUP = "\u001D"

        /**
         * How many kinds may be stored.
         *
         * Six times what this build draws, so a downgrade that meets a newer build's kinds keeps
         * every one of them. It exists for the reason every cap in this package exists: a caller
         * stuck in a loop must not be able to grow one preferences string without bound.
         */
        const val MAX_KINDS = 32

        /** Resolves the three states the class note describes. Internal so a test can pin them. */
        internal fun read(stored: String?): Set<String> {
            if (stored.isNullOrBlank()) return DEFAULT_KINDS
            if (stored == EMPTY_SELECTION) return emptySet()
            val kinds = stored.split(GROUP).mapNotNull(::usable).toSet()
            // Non-blank but nothing in it survived: a truncated write, or a row this build cannot
            // read. An accident rather than an opinion, so it reads as the default.
            return kinds.ifEmpty { DEFAULT_KINDS }
        }

        /** An empty set is the reader's own choice and is written as such. See the class note. */
        internal fun write(kinds: Set<String>): String =
            if (kinds.isEmpty()) EMPTY_SELECTION else kinds.take(MAX_KINDS).joinToString(GROUP)

        /**
         * One kind id, or null if it cannot be one.
         *
         * Lowercase letters and underscores only, which is what every id in [ALL_KINDS] is and what
         * a sixth one would be. The check is what keeps [EMPTY_SELECTION] impossible to collide
         * with, and it also means no kind can ever contain the separator.
         */
        internal fun usable(kind: String?): String? {
            val clean = kind?.trim()?.lowercase() ?: return null
            if (clean.isEmpty() || clean.length > MAX_KIND_LENGTH) return null
            if (clean.any { it !in 'a'..'z' && it != '_' }) return null
            return clean
        }

        /** `economic` is eight characters; twenty-four leaves room for a compound name. */
        private const val MAX_KIND_LENGTH = 24
    }
}
