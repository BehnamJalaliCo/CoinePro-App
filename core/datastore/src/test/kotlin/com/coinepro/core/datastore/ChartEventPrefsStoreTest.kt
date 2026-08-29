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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartEventPrefsStoreTest {

    @Test
    fun `a reader who has never chosen gets news marks and nothing else on the axis`() = runTest {
        val store = ChartEventPrefsStore(FakeEventPrefsPreferences())

        // Five glyph types on one axis at once is a smear; news is the kind that pays for its
        // space on any instrument.
        assertEquals(setOf(ChartEventPrefsStore.KIND_NEWS), store.kinds().first())
    }

    @Test
    fun `switching a kind on adds it to the marks already there`() = runTest {
        val store = ChartEventPrefsStore(FakeEventPrefsPreferences())

        store.setKind(ChartEventPrefsStore.KIND_EARNINGS, on = true)

        assertEquals(
            setOf(ChartEventPrefsStore.KIND_NEWS, ChartEventPrefsStore.KIND_EARNINGS),
            store.kinds().first(),
        )
    }

    @Test
    fun `switching every kind off survives, instead of news coming back at the next launch`() = runTest {
        val store = ChartEventPrefsStore(FakeEventPrefsPreferences())

        store.setKind(ChartEventPrefsStore.KIND_NEWS, on = false)

        assertEquals(emptySet<String>(), store.kinds().first())
    }

    @Test
    fun `an explicitly empty selection is not written as an empty string`() = runTest {
        val backing = FakeEventPrefsPreferences()

        ChartEventPrefsStore(backing).setKind(ChartEventPrefsStore.KIND_NEWS, on = false)

        assertEquals(
            ChartEventPrefsStore.EMPTY_SELECTION,
            backing.data.first()[ChartEventPrefsStore.KINDS],
        )
    }

    @Test
    fun `a blank row left by a truncated write reads as the default rather than as nothing`() = runTest {
        val backing = FakeEventPrefsPreferences(
            mutablePreferencesOf(ChartEventPrefsStore.KINDS to ""),
        )

        assertEquals(
            ChartEventPrefsStore.DEFAULT_KINDS,
            ChartEventPrefsStore(backing).kinds().first(),
        )
    }

    @Test
    fun `a row of nothing but separators reads as the default, because it is an accident`() {
        assertEquals(ChartEventPrefsStore.DEFAULT_KINDS, ChartEventPrefsStore.read("\u001D\u001D"))
    }

    @Test
    fun `a kind from a newer build is kept rather than thrown away on a downgrade`() = runTest {
        val backing = FakeEventPrefsPreferences(
            mutablePreferencesOf(ChartEventPrefsStore.KINDS to "news\u001Dipo"),
        )

        val kinds = ChartEventPrefsStore(backing).kinds().first()

        assertTrue("ipo" in kinds)
        assertTrue(ChartEventPrefsStore.KIND_NEWS in kinds)
    }

    @Test
    fun `a token that cannot be a kind is dropped and its neighbours are kept`() {
        assertEquals(
            setOf(ChartEventPrefsStore.KIND_NEWS, ChartEventPrefsStore.KIND_SPLIT),
            ChartEventPrefsStore.read("news\u001Dnot a kind!\u001Dsplit"),
        )
    }

    @Test
    fun `switching an unreadable kind on changes nothing`() = runTest {
        val store = ChartEventPrefsStore(FakeEventPrefsPreferences())

        store.setKind("  ", on = true)

        assertEquals(ChartEventPrefsStore.DEFAULT_KINDS, store.kinds().first())
    }

    @Test
    fun `switching a kind off that was already off leaves the row alone`() = runTest {
        val backing = FakeEventPrefsPreferences()
        val store = ChartEventPrefsStore(backing)

        store.setKind(ChartEventPrefsStore.KIND_SPLIT, on = false)

        // Nothing was written, so the reader still has no stored opinion and still gets the default.
        assertEquals(ChartEventPrefsStore.DEFAULT_KINDS, store.kinds().first())
    }

    @Test
    fun `the cap holds so one preferences string cannot grow without bound`() = runTest {
        val store = ChartEventPrefsStore(FakeEventPrefsPreferences())

        (1..ChartEventPrefsStore.MAX_KINDS * 2).forEach { index ->
            store.setKind(kindName(index), on = true)
        }

        assertEquals(ChartEventPrefsStore.MAX_KINDS, store.kinds().first().size)
    }

    @Test
    fun `every kind the app offers survives a round trip`() = runTest {
        val store = ChartEventPrefsStore(FakeEventPrefsPreferences())

        ChartEventPrefsStore.ALL_KINDS.forEach { store.setKind(it, on = true) }

        assertEquals(ChartEventPrefsStore.ALL_KINDS.toSet(), store.kinds().first())
    }

    /**
     * A kind id that is only ever letters, because that is all [ChartEventPrefsStore] accepts —
     * a generated name carrying a digit would be refused and the cap would never be reached.
     */
    private fun kindName(index: Int): String {
        val letters = "abcdefghijklmnopqrstuvwxyz"
        return "kind_" + letters[index / letters.length % letters.length] + letters[index % letters.length]
    }
}

private class FakeEventPrefsPreferences(
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
