package com.coinepro.feature.screener

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.feature.screener.model.NumericOp
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerScreen
import com.coinepro.feature.screener.model.ScreenerSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The reader's own saved screens, on this device.
 *
 * ### There is no cap, no paywall and no gating
 *
 * Saved screens are unlimited and free, and this is the file where that would be quietly undone, so
 * it is written down here as well as in [ScreenerScreen]. [MAX_SCREENS] is a fuse against a caller
 * bug — a save inside a recomposition would otherwise grow one preferences string without bound —
 * and sits at a number no person reaches and no runaway loop stays under. It is not a product limit
 * and must never be presented to a reader as one.
 *
 * ### Why it lives in this module rather than in `core:datastore`
 *
 * A saved screen is made of [ScreenerFilter], [ScreenerField] and [ScreenerSort], which are this
 * feature's own types. Putting the store in `core:datastore` would mean either moving those types
 * down there — where nothing else would ever use them — or storing them as opaque strings and
 * parsing them back up here anyway. The screener is the only thing that will ever read this key.
 *
 * ### The encoding
 *
 * The delimited-string scheme the chart stores use, and for the same reason: the alternative is a
 * serialisation library for what is five fields and a list. All four of ASCII's separators are used
 * for what they are actually named for — file between screens, group between one screen's sections,
 * record between the items of a list, unit between one filter's own fields. They are control
 * characters, so nothing a reader can type contains one, and [ScreenerCodec.encode] refuses a screen
 * whose name somehow does rather than writing a record that would parse back as different fields.
 * Refused rather than sanitised: silently renaming somebody's screen is worse than not saving it.
 *
 * Decoding never throws and never fails a whole list. A filter this build does not recognise — an
 * indicator added in a later release, an operator that did not exist — is dropped and the rest of
 * the screen survives, because losing one condition is recoverable and losing the screen is not.
 */
class ScreenerStore(private val dataStore: DataStore<Preferences>) {

    /** Every saved screen, in the order they were saved. Empty on a device that has saved none. */
    val screens: Flow<List<ScreenerScreen>> = dataStore.data
        .map { preferences -> preferences[SCREENS].orEmpty() }
        .distinctUntilChanged()
        .map(ScreenerCodec::decodeAll)

    /**
     * Saves [screen], replacing any screen with the same [ScreenerScreen.id].
     *
     * Keyed on the id and not the name, so a reader may keep two screens called «تست» if they want
     * to. Nothing about a name is this module's business.
     */
    suspend fun save(screen: ScreenerScreen) {
        if (ScreenerCodec.encode(screen) == null) return
        dataStore.edit { preferences ->
            val existing = ScreenerCodec.decodeAll(preferences[SCREENS].orEmpty())
                .filterNot { it.id == screen.id }
            // `takeLast` rather than `take`, so the fuse discards the oldest rather than refusing
            // the save a reader just made. See the class note: nobody reaches this.
            preferences[SCREENS] = ScreenerCodec.encodeAll((existing + screen).takeLast(MAX_SCREENS))
        }
    }

    /** Forgets one screen. An id that is not stored is a no-op rather than an error. */
    suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            val remaining = ScreenerCodec.decodeAll(preferences[SCREENS].orEmpty())
                .filterNot { it.id == id }
            preferences[SCREENS] = ScreenerCodec.encodeAll(remaining)
        }
    }

    private companion object {
        val SCREENS = stringPreferencesKey("screener_saved_screens")

        /** A fuse against a caller bug, not a limit on the reader. See the class note. */
        const val MAX_SCREENS = 500
    }
}

/**
 * The pure half of [ScreenerStore]: text in, screens out, and back again.
 *
 * Separated so it can be tested without a `DataStore`, an Android runtime or a coroutine. Every
 * round-trip property this feature depends on — a name survives, an unknown filter is dropped rather
 * than fatal, a `BETWEEN` keeps both of its bounds — is a plain JVM assertion against this object.
 */
internal object ScreenerCodec {

    /** ASCII file separator, between two screens. */
    private const val FILE = "\u001C"

    /** ASCII group separator, between one screen's five sections. */
    private const val GROUP = "\u001D"

    /** ASCII record separator, between the items of a list section. */
    private const val RECORD = "\u001E"

    /** ASCII unit separator, between one filter's own fields. */
    private const val UNIT = "\u001F"

    /** Inside a [ScreenerFilter.Category]'s value set, which holds tickers and class names only. */
    private const val LIST = ","

    private val SEPARATORS = listOf(FILE, GROUP, RECORD, UNIT)

    fun encodeAll(screens: List<ScreenerScreen>): String =
        screens.mapNotNull(::encode).joinToString(FILE)

    fun decodeAll(stored: String): List<ScreenerScreen> = stored
        .split(FILE)
        .filter(String::isNotBlank)
        .mapNotNull(::decode)

    /** One screen as a line, or null when its name carries a separator and cannot be written. */
    fun encode(screen: ScreenerScreen): String? {
        if (screen.id.isBlank() || screen.name.isBlank()) return null
        if (SEPARATORS.any { screen.id.contains(it) || screen.name.contains(it) }) return null
        return listOf(
            screen.id,
            screen.name,
            encodeSort(screen.sort),
            screen.columns.joinToString(RECORD) { it.name },
            screen.filters.mapNotNull(::encodeFilter).joinToString(RECORD),
        ).joinToString(GROUP)
    }

    /**
     * One line back into a screen, or null when it carries no id.
     *
     * An id is the one field with no sensible default: a screen nothing can address cannot be
     * selected, renamed or deleted, so it is dropped. Everything else falls back — a missing sort
     * becomes the default sort, missing columns become the default columns, and an unreadable filter
     * is left out.
     */
    fun decode(record: String): ScreenerScreen? {
        val parts = record.split(GROUP)
        val id = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: return null
        val columns = parts.getOrNull(3).orEmpty()
            .split(RECORD)
            .mapNotNull { column -> ScreenerField.entries.firstOrNull { it.name == column } }
        return ScreenerScreen(
            id = id,
            name = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: id,
            filters = parts.getOrNull(4).orEmpty()
                .split(RECORD)
                .filter(String::isNotBlank)
                .mapNotNull(::decodeFilter),
            sort = parts.getOrNull(2)?.let(::decodeSort) ?: ScreenerSort.DEFAULT,
            columns = columns.ifEmpty { ScreenerField.DEFAULT_COLUMNS },
        )
    }

    private fun encodeSort(sort: ScreenerSort): String =
        sort.field.name + UNIT + if (sort.descending) "1" else "0"

    private fun decodeSort(encoded: String): ScreenerSort? {
        val parts = encoded.split(UNIT)
        val field = ScreenerField.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return null
        return ScreenerSort(field, descending = parts.getOrNull(1) != "0")
    }

    private fun encodeFilter(filter: ScreenerFilter): String? = when (filter) {
        is ScreenerFilter.Numeric -> listOf(
            "n", filter.field.name, filter.op.name, filter.value.toString(), filter.bound?.toString().orEmpty(),
        ).joinToString(UNIT)

        is ScreenerFilter.Category -> listOf(
            "c", filter.field.name, filter.values.joinToString(LIST),
        ).joinToString(UNIT)

        // A query carrying a separator is refused rather than trimmed, for the same reason a name
        // is: the alternative writes a record that parses back as a different filter.
        is ScreenerFilter.TextMatch ->
            if (SEPARATORS.any { filter.query.contains(it) }) null else "t" + UNIT + filter.query

        is ScreenerFilter.IndicatorFilter -> listOf(
            "i",
            filter.indicatorId,
            filter.period?.toString().orEmpty(),
            filter.op.name,
            filter.value.toString(),
            filter.bound?.toString().orEmpty(),
        ).joinToString(UNIT)
    }

    private fun decodeFilter(record: String): ScreenerFilter? {
        val parts = record.split(UNIT)
        return when (parts.getOrNull(0)) {
            "n" -> {
                val field = field(parts.getOrNull(1)) ?: return null
                val op = op(parts.getOrNull(2)) ?: return null
                val value = parts.getOrNull(3)?.toDoubleOrNull() ?: return null
                ScreenerFilter.Numeric(field, op, value, parts.getOrNull(4)?.toDoubleOrNull())
            }

            "c" -> {
                val field = field(parts.getOrNull(1)) ?: return null
                ScreenerFilter.Category(
                    field = field,
                    values = parts.getOrNull(2).orEmpty()
                        .split(LIST)
                        .filter(String::isNotBlank)
                        .toSet(),
                )
            }

            "t" -> parts.getOrNull(1)?.let(ScreenerFilter::TextMatch)

            "i" -> {
                val indicatorId = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
                val op = op(parts.getOrNull(3)) ?: return null
                val value = parts.getOrNull(4)?.toDoubleOrNull() ?: return null
                ScreenerFilter.IndicatorFilter(
                    indicatorId = indicatorId,
                    period = parts.getOrNull(2)?.toIntOrNull(),
                    op = op,
                    value = value,
                    bound = parts.getOrNull(5)?.toDoubleOrNull(),
                )
            }

            else -> null
        }
    }

    private fun field(name: String?): ScreenerField? =
        ScreenerField.entries.firstOrNull { it.name == name }

    private fun op(name: String?): NumericOp? =
        NumericOp.entries.firstOrNull { it.name == name }
}
