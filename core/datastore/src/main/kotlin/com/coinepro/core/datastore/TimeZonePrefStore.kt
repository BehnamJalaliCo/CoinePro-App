package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.ZoneId

/**
 * Which zone the chart's time axis is read in.
 *
 * ### Why the default is Tehran and not the phone's zone
 *
 * This app's readers are in Iran, and Iran is **UTC+03:30**. The half hour is not a curiosity; it
 * is the whole reason this store exists. Bar boundaries are computed on epoch seconds, and every
 * boundary above the hourly — the daily, the weekly, the monthly — has to be anchored to somebody's
 * midnight. Anchored to UTC, a "daily" bar for a Tehran reader opens at 03:30 their time, and every
 * daily candle on the chart is three and a half hours out of step with the day it claims to be.
 * Arithmetic on epoch seconds gets that wrong silently: it produces a chart that looks completely
 * normal and is not the one anybody else is looking at. `CHART_TIME_ZONE` in `core:marketdata` is
 * `Asia/Tehran` for exactly this reason, and [DEFAULT_ZONE_ID] is the same answer to the same
 * question, kept as a string on this side of the module boundary.
 *
 * ### There is a real bug today, and this store is what fixes it
 *
 * The bar buckets are cut in `Asia/Tehran` while the canvas reads the axis in the *device's* zone:
 * `formatTime` and `startsAPeriod` in `CoineProChart.kt` both call `ZoneId.systemDefault()`. On any
 * phone not set to Tehran the two disagree — the label under a bar names one day and the bold month
 * boundary falls on another, because one is asking the device and the other is asking Tehran. It is
 * not a display nicety: it is two parts of the same axis answering a different question. Both
 * should read this store, and then they agree with each other and with the buckets by construction.
 *
 * ### Why it is a setting rather than a constant
 *
 * A reader trading the New York session wants the axis in New York time, and one following the
 * London open wants London; that is what the zone picker on every desktop terminal is for. It is
 * also why the setting stores an id and not an offset: an offset cannot know about a daylight
 * change, and Iran itself dropped daylight saving in 2022, so an offset that was right in 2021 is
 * wrong for every bar drawn before that date. `ZoneId` handles that; a number cannot.
 *
 * ### One row, so no cap
 *
 * Every other store in this package documents how many rows it keeps, because each holds a list a
 * runaway caller could grow. This one holds a single id, so there is nothing to cap — said out loud
 * rather than left as an omission. What is bounded instead is the id's length, in [usable].
 *
 * ### Reading never throws
 *
 * An absent entry, a blank one, and one holding a zone this device's tzdb has never heard of — a
 * newer build's zone, or one retired from the database — all read back as [DEFAULT_ZONE_ID]. The
 * alternative is a chart that cannot draw an axis because of a string the app wrote itself, and an
 * axis is not optional.
 */
class TimeZonePrefStore(private val dataStore: DataStore<Preferences>) {

    /**
     * The zone id to read the axis in — `Asia/Tehran`, `America/New_York`, `UTC`.
     *
     * A `String` and not a `ZoneId`, so that this module keeps the shape every other store in the
     * package has and a caller in a different process is not forced to resolve a zone it does not
     * use. **The caller must hoist the resolution.** This flow is cheap in the sense that matters —
     * it is distinct-until-changed and DataStore only emits when something is written, so a chart
     * that collects it does the validation once per change and not once per frame — but
     * `ZoneId.of(id)` is a map lookup and an allocation, and the canvas formats a label for every
     * gridline on every frame. Resolve it once, next to where the state is collected, and pass the
     * `ZoneId` down; never call `ZoneId.of` inside a draw pass.
     */
    fun zone(): Flow<String> = dataStore.data
        .map { preferences -> usable(preferences[ZONE]) ?: DEFAULT_ZONE_ID }
        .distinctUntilChanged()

    /**
     * Records the reader's chosen zone.
     *
     * An id this device cannot resolve is ignored rather than stored, because storing it would put
     * the app one launch away from an axis it cannot draw, and the reader would have no way to see
     * which of their settings did it. A caller offering a picker should be listing
     * `ZoneId.getAvailableZoneIds()` anyway, so a rejection here means a bug on that side and not a
     * choice a person made.
     */
    suspend fun setZone(id: String) {
        val clean = usable(id) ?: return
        dataStore.edit { preferences -> preferences[ZONE] = clean }
    }

    companion object {
        internal val ZONE = stringPreferencesKey("chart_time_zone")

        /**
         * `Asia/Tehran`. Matches `CHART_TIME_ZONE` in `core:marketdata`, duplicated rather than
         * depended on so this module keeps its own dependencies — the same trade
         * [ChartDrawingStore.DEFAULT_COLOUR] makes. If one of the two ever moves, both move.
         */
        const val DEFAULT_ZONE_ID = "Asia/Tehran"

        /**
         * A stored id, or null if it is not a zone this device knows.
         *
         * The length check runs first so that a corrupt row cannot hand a long string to the tzdb
         * lookup, and the lookup itself goes through `runCatching` because `ZoneId.of` signals an
         * unknown zone by throwing — which is the wrong shape for a value read off disk, where "not
         * a zone any more" is a normal thing to find and not an error worth propagating.
         */
        internal fun usable(id: String?): String? {
            val clean = id?.trim() ?: return null
            if (clean.isEmpty() || clean.length > MAX_ID_LENGTH) return null
            return runCatching { ZoneId.of(clean).id }.getOrNull()
        }

        /** `America/Argentina/ComodRivadavia` is 32 characters; the longest in tzdb is under 40. */
        private const val MAX_ID_LENGTH = 64
    }
}
