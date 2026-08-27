package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A saved chart setup: the type, the timeframe, and the indicators that were switched on.
 *
 * Not the drawings. Drawings are anchored to one instrument's price and time and mean nothing on
 * another; a layout is the *apparatus* a reader looks through, and the whole point is that it
 * carries from symbol to symbol. Saving drawings into a layout would paste last week's trend lines
 * onto whatever chart it was applied to.
 */
data class ChartLayout(
    val name: String,
    val chartTypeId: String,
    val timeframeId: String,
    val indicatorIds: List<String>,
)

/**
 * The reader's own layouts.
 *
 * Local, and stored as one delimited string per layout rather than as JSON, because the alternative
 * is a serialisation library in a preferences module for four fields. The delimiters are ASCII's
 * own record and unit separators — control characters, so no name, ticker or indicator id can
 * contain one — and [encode] refuses a name carrying one rather than writing a record that would
 * parse back as different fields.
 */
class ChartLayoutStore(private val dataStore: DataStore<Preferences>) {

    val layouts: Flow<List<ChartLayout>> = dataStore.data.map { preferences ->
        preferences[LAYOUTS].orEmpty()
            .split(RECORD)
            .filter(String::isNotBlank)
            .mapNotNull(::decode)
    }

    /** Saves under [ChartLayout.name], replacing a layout of that name rather than duplicating it. */
    suspend fun save(layout: ChartLayout) {
        if (encode(layout) == null) return
        dataStore.edit { preferences ->
            val existing = preferences[LAYOUTS].orEmpty()
                .split(RECORD)
                .filter(String::isNotBlank)
                .mapNotNull(::decode)
                .filterNot { it.name == layout.name }
            preferences[LAYOUTS] = (existing + layout).mapNotNull(::encode).joinToString(RECORD)
        }
    }

    suspend fun delete(name: String) {
        dataStore.edit { preferences ->
            preferences[LAYOUTS] = preferences[LAYOUTS].orEmpty()
                .split(RECORD)
                .filter(String::isNotBlank)
                .mapNotNull(::decode)
                .filterNot { it.name == name }
                .mapNotNull(::encode)
                .joinToString(RECORD)
        }
    }

    private companion object {
        val LAYOUTS = stringPreferencesKey("chart_layouts")

        /** ASCII record separator. Chosen because nothing a reader can type contains it. */
        const val RECORD = "\u001E"

        /** ASCII unit separator, for the fields inside one record. */
        const val UNIT = "\u001F"
        const val LIST = ","

        fun encode(layout: ChartLayout): String? {
            // A name carrying a separator would parse back as two fields. Refused rather than
            // sanitised: silently renaming somebody's layout is worse than not saving it.
            if (layout.name.isBlank() || layout.name.contains(RECORD) || layout.name.contains(UNIT)) {
                return null
            }
            return listOf(
                layout.name,
                layout.chartTypeId,
                layout.timeframeId,
                layout.indicatorIds.joinToString(LIST),
            ).joinToString(UNIT)
        }

        fun decode(record: String): ChartLayout? {
            val parts = record.split(UNIT)
            if (parts.size < 4) return null
            return ChartLayout(
                name = parts[0],
                chartTypeId = parts[1],
                timeframeId = parts[2],
                indicatorIds = parts[3].split(LIST).filter(String::isNotBlank),
            )
        }
    }
}
