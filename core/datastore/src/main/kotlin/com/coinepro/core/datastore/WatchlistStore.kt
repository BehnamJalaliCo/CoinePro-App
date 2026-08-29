package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.core.notifications.AlertScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * One named list of instruments the reader chose to keep an eye on.
 *
 * [id] is generated once and never shown. Everything that points at a list — the active-list
 * pointer, an `AlertScope.Watchlist`, the per-list column set — keys off it rather than off
 * [name], because a rename is the most ordinary thing a reader does to a list and it must not
 * orphan the alerts pointing at it. [AlertScope.Watchlist.DEFAULT_LIST_ID] is the id every alert
 * written before named lists existed carries, so the list holding that id is the one this store
 * refuses to delete.
 *
 * [symbols] is in **insertion order, oldest first**, and stays that way. The reader put them in a
 * sequence and the sequence is information; a personal list that rearranges itself because the
 * market moved is the one thing a personal list must never do. Sorting by a column is a *view*
 * over this order, held in [WatchlistSort], not a rewrite of it — which is why switching the sort
 * back to manual restores exactly what the reader dragged into place.
 *
 * [createdAt] and [updatedAt] are epoch milliseconds and exist so a future screen can say when a
 * list was last touched. Nothing orders by them today; [WatchlistStore.lists] deliberately orders
 * by creation so the switcher does not reshuffle itself every time a symbol is starred.
 */
data class Watchlist(
    val id: String,
    val name: String,
    val symbols: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** Whether this is the list that alerts fall back to, and so cannot be deleted. */
    val isDefault: Boolean get() = id == DEFAULT_LIST_ID

    companion object {
        /**
         * The id of the list that always exists.
         *
         * Taken from [AlertScope.Watchlist.DEFAULT_LIST_ID] rather than spelled out again, because
         * the two have to be the same string for a watchlist alert to resolve at all. Written as
         * a `const` alias so that the day the alert side moves, this moves with it instead of
         * quietly disagreeing and leaving every watchlist alert resolving to nothing.
         */
        const val DEFAULT_LIST_ID = AlertScope.Watchlist.DEFAULT_LIST_ID

        /**
         * What the always-present list is called before anybody renames it.
         *
         * A Persian string in a storage layer is a deviation from what the rest of this module
         * does, and it is deliberate: this name is *stored user data* from the first launch — the
         * reader can rename it, and a name the feature module substituted at draw time would snap
         * back to the substitute the moment they cleared it. It matches `markets_watchlist` in
         * `feature:search`, which is the word the tab already uses.
         */
        const val DEFAULT_LIST_NAME = "دیده‌بان"

        /** The longest a list name may be. Long enough to be descriptive, short enough to fit a chip. */
        const val MAX_NAME_LENGTH = 40
    }
}

/**
 * A colour a reader can put against one symbol in one list.
 *
 * Seven, and seven is a decision rather than a round number. Flags are read at a glance from a
 * 3dp bar at the edge of a row, and past roughly seven hues the reader stops recognising which is
 * which and starts having to think — at which point a flag is slower than reading the ticker. The
 * seven here are the six TradingView ships plus a neutral grey for "seen, no opinion", and they
 * are spaced far enough apart in hue that red/orange and blue/purple stay separable on the small
 * bar as well as on the big swatch in the picker.
 *
 * [argb] is a packed colour rather than a Compose `Color` for the same reason
 * [ChartColourTemplate]'s are: `core:datastore` does not depend on Compose and must not, because
 * this module is read by plain unit tests and by the widget process.
 * `Color(value.toULong() shl 32)` is the whole conversion on the other side.
 *
 * [persianName] lives here rather than in the feature module — the other deviation this file
 * makes — because the seven are a closed vocabulary that no reader edits, and splitting a fixed
 * seven-entry table across two modules buys nothing but a chance for them to drift.
 *
 * [id] is what is written to disk. Never the ordinal: inserting an eighth flag in the middle would
 * silently repaint every stored row.
 */
enum class WatchlistFlag(val id: String, val argb: Long, val persianName: String) {
    /** The strongest of the seven. Readers use it for what they are about to act on. */
    RED("red", 0xFFE5484D, "قرمز"),

    /** Warm, and clearly not the red beside it even on a 3dp bar. */
    ORANGE("orange", 0xFFF2851F, "نارنجی"),

    /** Deliberately the app's gold rather than a pure yellow, which vanishes on the light theme. */
    YELLOW("yellow", 0xFFD8A848, "زرد"),

    /** The palette's own buy green, so a green flag never argues with a green figure. */
    GREEN("green", 0xFF00B15C, "سبز"),

    /** Cool and unambiguous; the most-used flag in every app that has them. */
    BLUE("blue", 0xFF3B82F6, "آبی"),

    /** Far enough from the blue in hue to survive the narrow bar. */
    PURPLE("purple", 0xFF9061F9, "بنفش"),

    /** For "looked at it, no view yet". Neutral rather than absent, which is a different state. */
    GREY("grey", 0xFF8A94A6, "خاکستری"),
    ;

    companion object {
        /** The flag with this stored id, or null for one this build does not know. */
        fun ofId(id: String): WatchlistFlag? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What kind of figure a column holds, so the row knows how to render it.
 *
 * The row cannot work this out from the number: `1.08` is a price, a signed change and a
 * percentage in three different columns, and each wants different decimals, a different sign rule
 * and a different suffix. Carrying the unit on the column is what stops the renderer growing a
 * `when` over column identity in every place a cell is drawn.
 */
enum class WatchlistColumnUnit {
    /** An instrument price. Decimals chosen from magnitude, no sign, no suffix. */
    PRICE,

    /** A move in price. Always carries an explicit `+` or `−`; no suffix. */
    SIGNED_PRICE,

    /** A move in percent. Signed, and the only unit that appends `٪`. */
    PERCENT,

    /** Traded quantity in the base asset — compacted, because it runs to nine figures. */
    BASE_AMOUNT,

    /** Traded value in the quote asset. Compacted for the same reason. */
    QUOTE_AMOUNT,

    /** Not a number at all. The flag column, which draws a swatch. */
    NONE,
}

/**
 * A column a reader can put on their watchlist rows.
 *
 * [id] is the stored form and is stable. [persianLabel] is the heading; it is here rather than in
 * a string resource because the set is closed and the heading has to survive a caller that draws
 * columns generically — a `stringResource` lookup per column would push a Compose dependency into
 * the enum that decides what the columns *are*.
 */
enum class WatchlistColumn(
    val id: String,
    val persianLabel: String,
    val unit: WatchlistColumnUnit,
) {
    /** The colour bar. Not a figure, and always drawn at the row's leading edge. */
    FLAG("flag", "پرچم", WatchlistColumnUnit.NONE),

    /** The last traded price. The one column no watchlist is useful without. */
    LAST_PRICE("last", "آخرین", WatchlistColumnUnit.PRICE),

    /** The move since the session open, in price. */
    CHANGE("change", "تغییر", WatchlistColumnUnit.SIGNED_PRICE),

    /** The same move in percent, which is what a list is actually scanned for. */
    CHANGE_PERCENT("change_percent", "تغییر ٪", WatchlistColumnUnit.PERCENT),

    /** The session high. */
    DAY_HIGH("day_high", "بیشترین", WatchlistColumnUnit.PRICE),

    /** The session low. */
    DAY_LOW("day_low", "کمترین", WatchlistColumnUnit.PRICE),

    /** Traded quantity in the base asset over the session. */
    VOLUME("volume", "حجم", WatchlistColumnUnit.BASE_AMOUNT),

    /** Traded value in the quote asset over the session — comparable across instruments. */
    QUOTE_VOLUME("quote_volume", "ارزش معاملات", WatchlistColumnUnit.QUOTE_AMOUNT),
    ;

    /** Whether this column holds a market figure, and so is rendered in Latin digits, right-aligned. */
    val isFigure: Boolean get() = unit != WatchlistColumnUnit.NONE

    companion object {
        /**
         * What a watchlist row shows until the reader says otherwise.
         *
         * Three, and the number is arithmetic rather than taste. At 411dp — the reference
         * device, and the width this whole design system is measured against — a row spends 16dp
         * of gutter at each edge, leaving 379. Before the figures it spends 3 on the flag rail,
         * 34 on the reorder grip, 30 on the asset logo, 96 on the ticker and its Persian name, and
         * 12 between each of those four: 211 in all. The price column is 92 and the percentage
         * pill 64, with 8 between them, which is 164. Two figure columns land at 375 of the 379
         * available. A third at 78 would overflow, and the first thing to be cut would be the
         * price, which is the one thing a market row may not truncate.
         *
         * So the default is [FLAG], [LAST_PRICE] and [CHANGE_PERCENT]: the colour the reader
         * assigned, the number they came for, and the move they scan for. Everything else is one
         * tap away in the column control, and choosing more than fits is the reader's business —
         * the row scrolls its figure block sideways, in step with its neighbours and with the
         * headings, rather than refusing.
         */
        val DEFAULT: Set<WatchlistColumn> = setOf(FLAG, LAST_PRICE, CHANGE_PERCENT)

        /** The column with this stored id, or null for one this build does not know. */
        fun ofId(id: String): WatchlistColumn? = entries.firstOrNull { it.id == id }
    }
}

/**
 * How one list is ordered on screen.
 *
 * [column] is null for the reader's own order, which is the default and the point of a watchlist.
 * A stored sort is a *view*: the underlying [Watchlist.symbols] order is never rewritten, so
 * turning the sort off puts the list back exactly as it was dragged.
 *
 * [descending] means largest first, which is what somebody sorting by change or by volume wants on
 * the first tap. It is meaningless while [column] is null and is kept anyway, so that turning a
 * sort off and on again does not silently flip its direction.
 */
data class WatchlistSort(
    val column: WatchlistColumn? = null,
    val descending: Boolean = true,
) {
    /** Whether the reader's dragged order is what is shown. */
    val isManual: Boolean get() = column == null

    companion object {
        /** The reader's own order, which is what a new list starts in. */
        val Manual = WatchlistSort()
    }
}

/**
 * Everything about a list that is not its symbols: the flags on them, the columns, the sort.
 *
 * Read as one object rather than as three flows, because a row needs all three to draw and three
 * separate collections of the same preferences file would recompose the list three times for one
 * edit.
 */
data class WatchlistSettings(
    /** Symbol to flag, holding only the symbols the reader actually flagged. */
    val flags: Map<String, WatchlistFlag> = emptyMap(),
    val columns: Set<WatchlistColumn> = WatchlistColumn.DEFAULT,
    val sort: WatchlistSort = WatchlistSort.Manual,
)

/**
 * The result of reading a pasted or opened watchlist file.
 *
 * [rejected] is the reason this is a type rather than a `List<String>`. A silent import is the
 * worst kind: somebody pastes forty lines, thirty-eight arrive, and the two that did not are
 * discovered a week later when an alert they thought they had never fires. Every non-blank,
 * non-comment line that did not become a symbol is in here, verbatim, for the screen to show.
 */
data class WatchlistImport(
    /** Accepted tickers, uppercased, de-duplicated, in the order the file listed them. */
    val symbols: List<String>,
    /** Lines that carried something and yielded no symbol, exactly as they were written. */
    val rejected: List<String> = emptyList(),
)

/**
 * Reading and writing the plain-text form a watchlist moves between apps in.
 *
 * Plain text and nothing else. Every app that exports a watchlist exports a list of tickers, one
 * per line, and the two dialects that exist are a `#` comment line and an `EXCHANGE:SYMBOL`
 * prefix — TradingView writes the second, which is why anybody pasting from TradingView into a
 * strict parser gets nothing. Both are handled here, in thirty lines, and that is the whole
 * feature: a format nobody has to be taught and no library has to be added to parse.
 *
 * A separate object rather than methods on the store, because parsing has nothing to do with
 * storage and the tests for it should not need a `DataStore`.
 */
object WatchlistTransfer {

    /**
     * Reads a pasted list.
     *
     * Blank lines and `#` comments are skipped rather than rejected — they are part of the format,
     * not damage. A duplicate is skipped too, and is not a rejection: the symbol *is* in the
     * resulting list, which is what the reader asked for. Anything else that carries characters no
     * ticker contains comes back in [WatchlistImport.rejected] so the screen can say so.
     *
     * The `EXCHANGE:SYMBOL` prefix is stripped from the *last* colon, so `BINANCE:BTCUSDT` and the
     * occasional `MT5:FX:EURUSD` both land on the ticker rather than on a fragment of a venue name.
     */
    fun parse(text: String): WatchlistImport {
        val accepted = LinkedHashSet<String>()
        val rejected = mutableListOf<String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(COMMENT)) return@forEach
            val ticker = line.substringAfterLast(EXCHANGE_SEPARATOR).trim().uppercase()
            if (ticker.isEmpty() || !ticker.all(::isTickerCharacter)) {
                rejected += line
                return@forEach
            }
            accepted += ticker
        }
        return WatchlistImport(symbols = accepted.toList(), rejected = rejected)
    }

    /**
     * Writes the plain tickers, one per line, with a trailing newline.
     *
     * No header, no venue prefix, no comment. The file is meant to be pasted into the next app's
     * import box, and every decoration is one more thing that box might not understand — the
     * trailing newline is the exception, because a file without one appends to whatever it is
     * concatenated with. Round-trips through [parse] exactly.
     */
    fun format(symbols: List<String>): String =
        symbols.joinToString(separator = "\n", postfix = "\n")

    /**
     * What a ticker may contain.
     *
     * Latin letters and digits, plus the four punctuation marks real instruments use: the dot in
     * `BRK.B`, the hyphen in `BTC-USD`, the slash in `EUR/USD` and the underscore some venues put
     * in perpetuals. Anything else — a Persian letter, a space, a comma, one of this file's own
     * control-character separators — means the line was not a ticker.
     */
    private fun isTickerCharacter(character: Char): Boolean =
        character in 'A'..'Z' || character in '0'..'9' || character in ".-_/"

    /** A line that starts with this is a note to a human, not a symbol. */
    private const val COMMENT = "#"

    /** What TradingView and most terminals put between a venue and a ticker. */
    private const val EXCHANGE_SEPARATOR = ":"
}

/**
 * The reader's watchlists: several of them, named, flagged, and each with its own columns.
 *
 * ### Local, and deliberately so
 *
 * TradeYar does serve a watchlist, but behind its own device-link flow — a second identity to
 * establish before the first star can be placed. That is the wrong price for this feature. A
 * watchlist has to answer instantly, work with no signal, and survive a server being down; a round
 * trip per star turns the most-tapped control in a trading app into the slowest one. Sync is asked
 * for in `docs/REQUEST4_ACCOUNT_DELETION.md` and belongs on top of this, not instead of it.
 *
 * ### There is no limit on how many lists a reader may keep
 *
 * That is a product decision and it is the whole point of this file. The obvious competitor's free
 * tier allows exactly **one** watchlist and charges for the second, and charging for the second is
 * one of the things this app deliberately does not do. [MAX_LISTS] is fifty and exists only so
 * that a caller stuck in a loop cannot grow one preferences string without bound; nobody reaches
 * it by hand and no runaway stays under it.
 *
 * [MAX_SYMBOLS] is one thousand per list. The number is not invented either: TradingView's free
 * tier caps a list at thirty and its paid tiers at a thousand, so a thousand is the ceiling
 * somebody paying for the competition already lives under, and matching it means nobody arrives
 * here from a paid account and finds their list truncated.
 *
 * ### The default list cannot be deleted
 *
 * [Watchlist.DEFAULT_LIST_ID] is the id every `AlertScope.Watchlist` written before this file grew
 * named lists carries, and membership is resolved at evaluation time — so deleting that list would
 * not delete those alerts, it would leave them resolving to nothing, firing never, and looking
 * exactly like alerts that work. [delete] refuses it. It can be renamed and it can be emptied,
 * both of which are visible to the reader who did them.
 *
 * ### The encoding, and the migration
 *
 * The delimited-string scheme [ChartLayoutStore] and [SymbolChartStateStore] use, for the same
 * reason: the alternative is a serialisation library in a preferences module. ASCII's group
 * separator between lists, its record separator between one list's fields, its unit separator
 * inside the symbol list, the flag map and the column set. All three are control characters, so
 * nothing a reader can type contains one — and a name that somehow does is refused rather than
 * written as a record that would parse back as different fields.
 *
 * There is **no version marker** in what the previous version of this store wrote: it was one
 * preferences key, `watchlist_symbols`, holding tickers joined by a vertical bar. So the shape is
 * detected rather than a version consulted. On any read where the new key is blank, the old key is
 * lifted into the default list; on the first write after that the old key is removed, so an
 * emptied default list does not resurrect itself from the legacy string. A reader who opens this
 * update finds their watchlist where they left it, which is the only acceptable outcome — an empty
 * watchlist after an update is indistinguishable from data loss, and it is data loss.
 */
class WatchlistStore(
    private val dataStore: DataStore<Preferences>,
    /** Injectable so a test can assert on `createdAt` without waiting for the wall clock. */
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * The symbols on the list the reader is currently looking at.
     *
     * Kept as a property, and kept first, because it is what every caller written before named
     * lists existed reads — the star on a market row, the widget, the alert resolver. It now means
     * "the active list" rather than "the list", which is the only reading of it that still makes
     * sense and the one those callers want.
     */
    val symbols: Flow<List<String>> = dataStore.data
        .map { preferences ->
            val lists = readLists(preferences)
            lists.firstOrNull { it.id == readActiveId(preferences, lists) }?.symbols.orEmpty()
        }
        .distinctUntilChanged()

    /**
     * Every list, the default one first and the rest in the order they were made.
     *
     * Not sorted by name and not by when they were last touched. A switcher whose entries move
     * because a symbol was starred is a switcher the reader has to re-read every time, and the
     * default list is pinned to the front because it is the one that always exists and the one
     * alerts fall back to.
     */
    fun lists(): Flow<List<Watchlist>> = dataStore.data
        .map { preferences -> readLists(preferences) }
        .distinctUntilChanged()

    /**
     * One list's symbols, optionally only those carrying [flag].
     *
     * Filtering is the reason flags exist at all — a colour nobody can filter by is decoration —
     * so it is offered here rather than left to every caller to write again over [lists].
     */
    fun symbols(listId: String, flag: WatchlistFlag? = null): Flow<List<String>> = dataStore.data
        .map { preferences ->
            val symbols = readLists(preferences).firstOrNull { it.id == listId }?.symbols.orEmpty()
            if (flag == null) {
                symbols
            } else {
                val flags = readSettings(preferences, listId).flags
                symbols.filter { flags[it] == flag }
            }
        }
        .distinctUntilChanged()

    /**
     * The flags, columns and sort belonging to one list.
     *
     * A list this store has never heard of reads back as the defaults rather than as null, because
     * every caller of this would otherwise write the same `?: WatchlistSettings()` and one of them
     * would forget.
     */
    fun settings(listId: String): Flow<WatchlistSettings> = dataStore.data
        .map { preferences -> readSettings(preferences, listId) }
        .distinctUntilChanged()

    /** Which list the watchlist screen is showing. Always an id that exists. */
    fun activeListId(): Flow<String> = dataStore.data
        .map { preferences -> readActiveId(preferences, readLists(preferences)) }
        .distinctUntilChanged()

    /**
     * Points the watchlist screen at a list.
     *
     * An id that does not exist is ignored rather than stored. A dangling pointer would send the
     * next open looking for a list that is not there, and recovering from that is
     * indistinguishable from never having chosen one.
     */
    suspend fun setActiveList(id: String) {
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            if (lists.none { it.id == id }) return@edit
            writeLists(preferences, lists)
            preferences[ACTIVE] = id
        }
    }

    /**
     * Makes a list and returns its id.
     *
     * Returns an empty string when the name is unusable or the sanity cap is reached, so the
     * caller can tell the reader instead of switching to a list that was never made. An id is a
     * random one rather than the name, because two lists may be called the same thing and because
     * a rename must not move the list.
     */
    suspend fun create(name: String): String {
        val cleaned = cleanName(name) ?: return ""
        var created = ""
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            if (lists.size >= MAX_LISTS) return@edit
            val timestamp = now()
            created = "list_" + UUID.randomUUID().toString().replace("-", "").take(12)
            writeLists(
                preferences,
                lists + Watchlist(
                    id = created,
                    name = cleaned,
                    symbols = emptyList(),
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
        }
        return created
    }

    /**
     * Renames one list, keeping its id and everything pointing at it.
     *
     * The default list may be renamed like any other — it is only its *deletion* that would break
     * an alert, and a reader who wants to call their main list something else is not doing
     * anything dangerous.
     */
    suspend fun rename(id: String, name: String) {
        val cleaned = cleanName(name) ?: return
        editLists { lists ->
            if (lists.none { it.id == id }) return@editLists null
            lists.map { if (it.id == id) it.copy(name = cleaned, updatedAt = now()) else it }
        }
    }

    /**
     * Removes a list, unless it is the default one.
     *
     * Refusing is a no-op rather than an exception. The caller's job is to not offer the button —
     * a storage layer throwing here would turn a missing menu item into a crash — and the class
     * note explains why the default list has to survive.
     */
    suspend fun delete(id: String) {
        if (id == Watchlist.DEFAULT_LIST_ID) return
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            if (lists.none { it.id == id }) return@edit
            writeLists(preferences, lists.filterNot { it.id == id })
            preferences.remove(settingsKey(id))
            if (preferences[ACTIVE] == id) preferences.remove(ACTIVE)
        }
    }

    /**
     * Puts a symbol at the end of a list.
     *
     * At the end, never at the top: the order is the reader's and a new entry appended is a new
     * entry they can find, whereas one inserted at the front moves everything they had already
     * learned the position of. Already present is a no-op, and so is a list already at
     * [MAX_SYMBOLS].
     */
    suspend fun add(listId: String, symbol: String) {
        val ticker = normalise(symbol) ?: return
        editLists { lists ->
            val list = lists.firstOrNull { it.id == listId } ?: return@editLists null
            if (ticker in list.symbols || list.symbols.size >= MAX_SYMBOLS) return@editLists null
            lists.map {
                if (it.id == listId) {
                    it.copy(symbols = it.symbols + ticker, updatedAt = now())
                } else {
                    it
                }
            }
        }
    }

    /** Takes a symbol off a list, and drops the flag that was on it. */
    suspend fun remove(listId: String, symbol: String) {
        val ticker = normalise(symbol) ?: return
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            val list = lists.firstOrNull { it.id == listId } ?: return@edit
            if (ticker !in list.symbols) return@edit
            writeLists(
                preferences,
                lists.map {
                    if (it.id == listId) {
                        it.copy(symbols = it.symbols - ticker, updatedAt = now())
                    } else {
                        it
                    }
                },
            )
            // A flag on a symbol that is no longer in the list is invisible and would come back
            // wearing a colour the reader does not remember choosing if they ever re-add it.
            val settings = readSettings(preferences, listId)
            if (settings.flags.containsKey(ticker)) {
                writeSettings(preferences, listId, settings.copy(flags = settings.flags - ticker))
            }
        }
    }

    /**
     * Moves the symbol at [from] to [to], which is what a drag reports.
     *
     * Indices rather than a symbol and a target, because that is what a reorderable list gives the
     * caller and converting one to the other in the screen would mean the screen holding a copy of
     * the order to look the symbol up in. Out-of-range indices are a no-op: a drag that ended
     * outside the list is a drag the reader abandoned.
     */
    suspend fun move(listId: String, from: Int, to: Int) {
        if (from == to) return
        editLists { lists ->
            val list = lists.firstOrNull { it.id == listId } ?: return@editLists null
            if (from !in list.symbols.indices || to !in list.symbols.indices) return@editLists null
            val reordered = list.symbols.toMutableList()
            reordered.add(to, reordered.removeAt(from))
            lists.map { if (it.id == listId) it.copy(symbols = reordered, updatedAt = now()) else it }
        }
    }

    /**
     * Puts a colour against one symbol in one list, or clears it when passed null.
     *
     * Per list rather than per symbol, deliberately. The same instrument means different things in
     * two lists — red in "positions I hold" and blue in "watching for an entry" — and a flag that
     * followed the symbol everywhere would make the second list lie about the first.
     */
    suspend fun flag(listId: String, symbol: String, flag: WatchlistFlag?) {
        val ticker = normalise(symbol) ?: return
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            val list = lists.firstOrNull { it.id == listId } ?: return@edit
            if (ticker !in list.symbols) return@edit
            writeLists(preferences, lists)
            val settings = readSettings(preferences, listId)
            val flags = if (flag == null) settings.flags - ticker else settings.flags + (ticker to flag)
            writeSettings(preferences, listId, settings.copy(flags = flags))
        }
    }

    /**
     * Chooses which columns a list's rows show.
     *
     * An empty set is refused and the stored choice stands: a row with no columns is a row with
     * nothing on it but a logo, and a reader who unticked the last box did not ask for that.
     */
    suspend fun setColumns(listId: String, columns: Set<WatchlistColumn>) {
        if (columns.isEmpty()) return
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            if (lists.none { it.id == listId }) return@edit
            writeLists(preferences, lists)
            writeSettings(preferences, listId, readSettings(preferences, listId).copy(columns = columns))
        }
    }

    /**
     * Chooses how a list is ordered on screen.
     *
     * Stored per list, because the sort belongs to what the list is *for*: a list of holdings is
     * read in the order they were bought, a list of candidates is read biggest-mover first, and
     * one setting shared between them would be wrong for one of them every time the reader
     * switched.
     */
    suspend fun setSort(listId: String, sort: WatchlistSort) {
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            if (lists.none { it.id == listId }) return@edit
            writeLists(preferences, lists)
            writeSettings(preferences, listId, readSettings(preferences, listId).copy(sort = sort))
        }
    }

    /**
     * Adds or removes on the active list, and returns nothing.
     *
     * One entry point rather than `add` and `remove`, because the caller is a star that is already
     * showing the current state: two methods would let a screen decide to add something already
     * present, and the duplicate would sit in the list looking like a bug in the feed. [add] and
     * [remove] exist beside it for the watchlist screen, which addresses a list by id and knows
     * exactly which of the two it means.
     */
    suspend fun toggle(symbol: String) {
        val ticker = normalise(symbol) ?: return
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            val activeId = readActiveId(preferences, lists)
            val active = lists.firstOrNull { it.id == activeId } ?: return@edit
            val present = ticker in active.symbols
            if (!present && active.symbols.size >= MAX_SYMBOLS) return@edit
            val next = if (present) active.symbols - ticker else active.symbols + ticker
            writeLists(
                preferences,
                lists.map { if (it.id == activeId) it.copy(symbols = next, updatedAt = now()) else it },
            )
            if (present) {
                val settings = readSettings(preferences, activeId)
                if (settings.flags.containsKey(ticker)) {
                    writeSettings(preferences, activeId, settings.copy(flags = settings.flags - ticker))
                }
            }
        }
    }

    /** Empties the active list, keeping the list itself and its columns. */
    suspend fun clear() {
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            val activeId = readActiveId(preferences, lists)
            writeLists(
                preferences,
                lists.map {
                    if (it.id == activeId) it.copy(symbols = emptyList(), updatedAt = now()) else it
                },
            )
            writeSettings(
                preferences,
                activeId,
                readSettings(preferences, activeId).copy(flags = emptyMap()),
            )
        }
    }

    /**
     * Reads a pasted list into an existing one and reports what did not fit.
     *
     * Appends rather than replaces. Somebody importing into a list they have been keeping for a
     * year has not asked for it to be thrown away, and a replace is one undo the app does not
     * offer; a reader who wants a clean slate can make a new list, which costs one tap and is free
     * here in a way it is not on the competition.
     *
     * The returned [WatchlistImport.rejected] carries both the lines that were not tickers and the
     * tickers that were dropped at [MAX_SYMBOLS] — everything the reader pasted that is not now in
     * their list, in other words, which is the only honest definition of rejected.
     */
    suspend fun importInto(listId: String, text: String): WatchlistImport {
        val parsed = WatchlistTransfer.parse(text)
        var overflow = emptyList<String>()
        dataStore.edit { preferences ->
            val lists = readLists(preferences)
            val list = lists.firstOrNull { it.id == listId } ?: run {
                overflow = parsed.symbols
                return@edit
            }
            val room = MAX_SYMBOLS - list.symbols.size
            val fresh = parsed.symbols.filterNot { it in list.symbols }
            overflow = fresh.drop(room.coerceAtLeast(0))
            val added = fresh.take(room.coerceAtLeast(0))
            if (added.isEmpty()) return@edit
            writeLists(
                preferences,
                lists.map {
                    if (it.id == listId) it.copy(symbols = it.symbols + added, updatedAt = now()) else it
                },
            )
        }
        return parsed.copy(rejected = parsed.rejected + overflow)
    }

    /** The plain-text form of one list, ready to be shared or saved. See [WatchlistTransfer.format]. */
    suspend fun export(listId: String): String =
        WatchlistTransfer.format(lists().first().firstOrNull { it.id == listId }?.symbols.orEmpty())

    // ── reading ──────────────────────────────────────────────────────────────────────────────

    /**
     * Every list, with the default one guaranteed present and first.
     *
     * This is where the migration lives. See the class note: the previous version wrote one key
     * with no version marker, so the old shape is detected — the new key blank and the old one not
     * — and lifted into the default list. It is a *read-time* lift, which means a reader who
     * installs the update and never writes anything still sees their watchlist, and the write only
     * happens the next time they touch something.
     */
    private fun readLists(preferences: Preferences): List<Watchlist> {
        val stored = preferences[LISTS].orEmpty()
        val decoded = decodeLists(stored)
        val existing = decoded.firstOrNull { it.id == Watchlist.DEFAULT_LIST_ID }
        val default = existing ?: Watchlist(
            id = Watchlist.DEFAULT_LIST_ID,
            name = Watchlist.DEFAULT_LIST_NAME,
            symbols = if (stored.isBlank()) legacySymbols(preferences) else emptyList(),
        )
        return listOf(default) + decoded.filterNot { it.id == Watchlist.DEFAULT_LIST_ID }
    }

    /** The single unnamed list the previous version of this file kept, or nothing. */
    private fun legacySymbols(preferences: Preferences): List<String> =
        preferences[LEGACY_SYMBOLS].orEmpty()
            .split(LEGACY_SEPARATOR)
            .filter(String::isNotBlank)
            .take(MAX_SYMBOLS)

    /** The stored active id if it still names a list, and the default list otherwise. */
    private fun readActiveId(preferences: Preferences, lists: List<Watchlist>): String {
        val stored = preferences[ACTIVE]?.takeIf(String::isNotBlank)
        return if (stored != null && lists.any { it.id == stored }) stored else Watchlist.DEFAULT_LIST_ID
    }

    private fun readSettings(preferences: Preferences, listId: String): WatchlistSettings =
        decodeSettings(preferences[settingsKey(listId)].orEmpty())

    // ── writing ──────────────────────────────────────────────────────────────────────────────

    /**
     * Applies a change to every list at once, or does nothing when the change returns null.
     *
     * Null rather than the unchanged list, so that a refused edit does not rewrite the preferences
     * string — a write of identical bytes still wakes every collector of [dataStore].
     */
    private suspend fun editLists(transform: (List<Watchlist>) -> List<Watchlist>?) {
        dataStore.edit { preferences ->
            val next = transform(readLists(preferences)) ?: return@edit
            writeLists(preferences, next)
        }
    }

    /**
     * Writes the lists and retires the legacy key.
     *
     * Removing [LEGACY_SYMBOLS] here rather than in a one-off migration step is what stops an
     * emptied default list refilling itself: once the new key holds a default record, the lift in
     * [readLists] no longer runs, and once the old key is gone there is nothing left to lift.
     */
    private fun writeLists(preferences: MutablePreferences, lists: List<Watchlist>) {
        preferences[LISTS] = lists
            .take(MAX_LISTS)
            .mapNotNull { encodeList(it) }
            .joinToString(GROUP)
        preferences.remove(LEGACY_SYMBOLS)
    }

    private fun writeSettings(
        preferences: MutablePreferences,
        listId: String,
        settings: WatchlistSettings,
    ) {
        preferences[settingsKey(listId)] = encodeSettings(settings)
    }

    /** Trimmed, capped, and refused outright if it carries a separator or is blank. */
    private fun cleanName(name: String): String? = name.trim()
        .take(Watchlist.MAX_NAME_LENGTH)
        .takeIf { it.isNotBlank() && !hasSeparator(it) }

    /** Uppercased and trimmed, so one instrument cannot appear twice in two cases. */
    private fun normalise(symbol: String): String? = symbol.trim().uppercase()
        .takeIf { it.isNotEmpty() && !hasSeparator(it) }

    companion object {
        /**
         * The new key. Not `watchlist_symbols`, which the previous version wrote in a completely
         * different shape and which [readLists] reads once, to lift, and then removes.
         */
        internal val LISTS = stringPreferencesKey("watchlist_lists_v2")

        /** The single unnamed list this store used to keep. Read once, then retired. */
        internal val LEGACY_SYMBOLS = stringPreferencesKey("watchlist_symbols")

        internal val ACTIVE = stringPreferencesKey("watchlist_active_list")

        /**
         * One preferences key per list for its flags, columns and sort.
         *
         * Separate from the list record so that flagging a symbol does not rewrite — and so cannot
         * corrupt — the string holding every list's membership. The id is already restricted to
         * characters a key tolerates by [create].
         */
        internal fun settingsKey(listId: String) = stringPreferencesKey("watchlist_view_$listId")

        /** A vertical bar: what the previous version joined tickers with. */
        private const val LEGACY_SEPARATOR = "|"

        /** Between lists. ASCII group separator. */
        private const val GROUP = "\u001D"

        /** Between one list's fields. ASCII record separator. */
        private const val RECORD = "\u001E"

        /** Inside the symbol list, the flag map and the column set. ASCII unit separator. */
        private const val UNIT = "\u001F"

        /** See the class note: a sanity cap, not a product limit. */
        const val MAX_LISTS = 50

        /** See the class note: TradingView's paid-tier ceiling, matched on purpose. */
        const val MAX_SYMBOLS = 1_000

        internal fun encodeList(list: Watchlist): String? {
            if (list.id.isBlank() || hasSeparator(list.id)) return null
            val name = list.name.takeIf { it.isNotBlank() && !hasSeparator(it) } ?: return null
            return listOf(
                list.id,
                name,
                list.symbols.filterNot(::hasSeparator).distinct().take(MAX_SYMBOLS).joinToString(UNIT),
                list.createdAt.toString(),
                list.updatedAt.toString(),
            ).joinToString(RECORD)
        }

        internal fun decodeLists(stored: String): List<Watchlist> = stored
            .split(GROUP)
            .filter(String::isNotBlank)
            .mapNotNull(::decodeList)
            .distinctBy(Watchlist::id)

        internal fun decodeList(record: String): Watchlist? {
            val parts = record.split(RECORD)
            val id = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: return null
            return Watchlist(
                id = id,
                // A record written before names were required reads back under its own id rather
                // than as a blank chip the reader cannot tell from its neighbours.
                name = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: id,
                symbols = parts.getOrNull(2).orEmpty()
                    .split(UNIT)
                    .filter(String::isNotBlank)
                    .take(MAX_SYMBOLS),
                createdAt = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
                updatedAt = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
            )
        }

        internal fun encodeSettings(settings: WatchlistSettings): String = listOf(
            settings.flags
                .filterKeys { !hasSeparator(it) }
                .flatMap { (symbol, flag) -> listOf(symbol, flag.id) }
                .joinToString(UNIT),
            settings.columns.joinToString(UNIT) { it.id },
            settings.sort.column?.id.orEmpty(),
            if (settings.sort.descending) "1" else "0",
        ).joinToString(RECORD)

        internal fun decodeSettings(record: String): WatchlistSettings {
            if (record.isBlank()) return WatchlistSettings()
            val parts = record.split(RECORD)
            val flagParts = parts.getOrNull(0).orEmpty().split(UNIT).filter(String::isNotBlank)
            val columns = parts.getOrNull(1).orEmpty()
                .split(UNIT)
                .mapNotNull { WatchlistColumn.ofId(it) }
                .toSet()
            return WatchlistSettings(
                // Alternating symbol and flag id. A trailing symbol with no colour drops that one
                // entry rather than shifting every pair after it by one.
                flags = flagParts.chunked(2).mapNotNull { pair ->
                    if (pair.size != 2) return@mapNotNull null
                    val flag = WatchlistFlag.ofId(pair[1]) ?: return@mapNotNull null
                    pair[0] to flag
                }.toMap(),
                // An unreadable column set falls back to the default rather than to none, because
                // none is a row with nothing on it.
                columns = columns.ifEmpty { WatchlistColumn.DEFAULT },
                sort = WatchlistSort(
                    column = parts.getOrNull(2)?.takeIf(String::isNotBlank)
                        ?.let { WatchlistColumn.ofId(it) },
                    descending = parts.getOrNull(3) != "0",
                ),
            )
        }

        /** A separator inside a stored field would shift every field after it. */
        private fun hasSeparator(value: String) =
            value.contains(GROUP) || value.contains(RECORD) || value.contains(UNIT)
    }
}
