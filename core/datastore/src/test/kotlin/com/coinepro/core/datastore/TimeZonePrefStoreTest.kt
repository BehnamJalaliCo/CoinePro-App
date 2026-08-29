package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TimeZonePrefStoreTest {

    @Test
    fun `a reader who has never chosen reads the axis in Tehran`() = runTest {
        val store = TimeZonePrefStore(FakeTimeZonePreferences())

        assertEquals(TimeZonePrefStore.DEFAULT_ZONE_ID, store.zone().first())
    }

    @Test
    fun `the default zone is the half-hour one, which is the reason this store exists`() {
        // Tehran is UTC+03:30. A daily bar bucketed on UTC opens at 03:30 for these readers and
        // every daily candle is out of step with the day it claims to be — silently, because the
        // arithmetic on epoch seconds still produces a chart that looks normal.
        val offset = ZoneId.of(TimeZonePrefStore.DEFAULT_ZONE_ID)
            .rules
            .getOffset(Instant.ofEpochSecond(1_775_000_000L))

        assertEquals(3 * 3_600 + 30 * 60, offset.totalSeconds)
    }

    @Test
    fun `a chosen zone round-trips`() = runTest {
        val store = TimeZonePrefStore(FakeTimeZonePreferences())

        store.setZone("America/New_York")

        assertEquals("America/New_York", store.zone().first())
    }

    @Test
    fun `UTC is storable, because a reader comparing venues asks for exactly that`() = runTest {
        val store = TimeZonePrefStore(FakeTimeZonePreferences())

        store.setZone("UTC")

        assertEquals("UTC", store.zone().first())
    }

    @Test
    fun `an id with whitespace around it is trimmed rather than refused`() = runTest {
        val store = TimeZonePrefStore(FakeTimeZonePreferences())

        store.setZone("  Europe/London  ")

        assertEquals("Europe/London", store.zone().first())
    }

    @Test
    fun `a zone this device cannot resolve reads back as the default instead of throwing`() = runTest {
        val backing = FakeTimeZonePreferences(
            mutablePreferencesOf(TimeZonePrefStore.ZONE to "Mars/Olympus"),
        )

        // A newer build's zone, or one retired from the database. An axis is not optional, so the
        // answer is the default rather than an exception on the way to drawing one.
        assertEquals(TimeZonePrefStore.DEFAULT_ZONE_ID, TimeZonePrefStore(backing).zone().first())
    }

    @Test
    fun `a blank row left by a truncated write reads as the default`() = runTest {
        val backing = FakeTimeZonePreferences(mutablePreferencesOf(TimeZonePrefStore.ZONE to ""))

        assertEquals(TimeZonePrefStore.DEFAULT_ZONE_ID, TimeZonePrefStore(backing).zone().first())
    }

    @Test
    fun `an unresolvable id is never stored, so the app is not one launch from an axis it cannot draw`() = runTest {
        val backing = FakeTimeZonePreferences()
        val store = TimeZonePrefStore(backing)
        store.setZone("Asia/Tokyo")

        store.setZone("Nowhere/AtAll")

        assertEquals("Asia/Tokyo", store.zone().first())
        assertEquals("Asia/Tokyo", backing.data.first()[TimeZonePrefStore.ZONE])
    }

    @Test
    fun `an absurdly long id is refused before it ever reaches the zone database`() {
        assertEquals(null, TimeZonePrefStore.usable("Asia/" + "x".repeat(500)))
    }
}

private class FakeTimeZonePreferences(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
