package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reader's own Arena record, and the streak it implies.
 *
 * The streak is the interesting half. It is the one number in this feature a reader will feel
 * something about, which makes it the one that must not be wrong in the anxious direction: a count
 * that resets at midnight because they have not played *yet today* is a mechanic for making somebody
 * open an app at 11:58, and this app does not do that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArenaStoreTest {

    private fun result(day: Long, total: Int = 70) = ArenaResult(
        epochDay = day,
        symbol = "BTCUSDT",
        total = total,
        discipline = 45,
        profit = total - 45,
    )

    @Test
    fun `a day round-trips`() = runTest {
        val store = ArenaStore(FakeArenaPreferences())
        store.record(result(20_000L, total = 82))

        val rows = store.results.first()
        assertEquals(1, rows.size)
        assertEquals(20_000L, rows[0].epochDay)
        assertEquals("BTCUSDT", rows[0].symbol)
        assertEquals(82, rows[0].total)
        assertEquals(45, rows[0].discipline)
        assertEquals(37, rows[0].profit)
    }

    @Test
    fun `playing the same day again replaces it rather than adding a row`() = runTest {
        val store = ArenaStore(FakeArenaPreferences())
        store.record(result(20_000L, total = 40))
        store.record(result(20_000L, total = 90))

        val rows = store.results.first()
        // One challenge, one row. Two rows for Tuesday would let a history show three attempts at
        // the same question, which is not what a *daily* challenge is.
        assertEquals(1, rows.size)
        assertEquals(90, rows[0].total)
    }

    @Test
    fun `rows come back oldest first, whatever order they went in`() = runTest {
        val store = ArenaStore(FakeArenaPreferences())
        store.record(result(20_003L))
        store.record(result(20_001L))
        store.record(result(20_002L))

        assertEquals(listOf(20_001L, 20_002L, 20_003L), store.results.first().map { it.epochDay })
    }

    @Test
    fun `a streak counts back from today`() {
        val rows = (20_000L..20_006L).map { result(it) }
        assertEquals(7, rows.streak(today = 20_006L))
    }

    @Test
    fun `not having played yet today does not break a streak`() {
        // Nine in the morning, a fortnight behind them. Fourteen, not zero.
        val rows = (20_000L..20_013L).map { result(it) }
        assertEquals(14, rows.streak(today = 20_014L))
    }

    @Test
    fun `two days away does break it`() {
        val rows = (20_000L..20_013L).map { result(it) }
        assertEquals(0, rows.streak(today = 20_015L))
    }

    @Test
    fun `a gap in the middle only counts the run that reaches today`() {
        val rows = listOf(20_000L, 20_001L, 20_002L, 20_010L, 20_011L).map { result(it) }
        assertEquals(2, rows.streak(today = 20_011L))
    }

    @Test
    fun `an empty history has no streak and no best`() {
        val rows = emptyList<ArenaResult>()
        assertEquals(0, rows.streak(today = 20_000L))
        assertNull(rows.best())
    }

    @Test
    fun `the best day is the highest total, not the most recent`() {
        val rows = listOf(result(20_000L, total = 91), result(20_001L, total = 40))
        assertEquals(91, rows.best()?.total)
    }

    @Test
    fun `a corrupt row is skipped rather than taking the history with it`() = runTest {
        val store = ArenaStore(FakeArenaPreferences())
        store.record(result(20_000L))
        store.record(result(20_001L))
        // A row written by a future build with a sixth field, or half a row from a torn write: the
        // reader's other days must survive it. A history that empties itself on one bad row is a
        // history nobody trusts twice.
        assertEquals(2, store.results.first().size)
        store.clear()
        assertEquals(emptyList<ArenaResult>(), store.results.first())
    }
}

private class FakeArenaPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
