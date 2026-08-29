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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SymbolChartStateStoreTest {

    private fun gold(updatedAt: Long = 1_700_000_000_000L) = SymbolChartState(
        symbol = "XAUUSD",
        timeframe = "H4",
        chartType = "CANDLES",
        indicators = listOf("ema", "rsi"),
        indicatorPeriods = mapOf("ema" to 21, "rsi" to 14),
        scaleMode = "LOGARITHMIC",
        logScale = true,
        updatedAt = updatedAt,
    )

    @Test
    fun `a symbol's chart state round-trips with every field intact`() = runTest {
        val store = SymbolChartStateStore(FakeStatePreferences())
        store.put(gold())

        assertEquals(gold(), store.state("XAUUSD").first())
    }

    @Test
    fun `a state with no indicators round-trips as empty collections, not as one holding a blank`() = runTest {
        val store = SymbolChartStateStore(FakeStatePreferences())
        store.put(SymbolChartState(symbol = "BTCUSDT", timeframe = "M5", updatedAt = 5L))

        val stored = store.state("BTCUSDT").first()
        assertEquals(emptyList<String>(), stored?.indicators)
        assertEquals(emptyMap<String, Int>(), stored?.indicatorPeriods)
        assertNull(stored?.chartType)
        assertNull(stored?.scaleMode)
    }

    @Test
    fun `the symbol is normalised so one instrument is never two rows`() = runTest {
        val store = SymbolChartStateStore(FakeStatePreferences())
        store.put(gold().copy(symbol = "xauusd", timeframe = "M15"))

        assertEquals(1, store.all().first().size)
        assertEquals("M15", store.state("XAUUSD").first()?.timeframe)
    }

    @Test
    fun `a symbol nobody has configured reads back as null rather than as a default row`() = runTest {
        val store = SymbolChartStateStore(FakeStatePreferences())
        store.put(gold())

        assertNull(store.state("ETHUSDT").first())
    }

    @Test
    fun `putting the same symbol twice replaces the row rather than appending a second`() = runTest {
        val store = SymbolChartStateStore(FakeStatePreferences())
        store.put(gold(updatedAt = 10L))
        store.put(gold(updatedAt = 20L).copy(timeframe = "D1", indicators = listOf("macd")))

        val all = store.all().first()
        assertEquals(1, all.size)
        assertEquals("D1", all.single().timeframe)
        assertEquals(listOf("macd"), all.single().indicators)
    }

    @Test
    fun `all lists every symbol most recently updated first`() = runTest {
        val store = SymbolChartStateStore(FakeStatePreferences())
        store.put(gold().copy(symbol = "AAA", updatedAt = 100L))
        store.put(gold().copy(symbol = "BBB", updatedAt = 300L))
        store.put(gold().copy(symbol = "CCC", updatedAt = 200L))

        assertEquals(listOf("BBB", "CCC", "AAA"), store.all().first().map(SymbolChartState::symbol))
    }

    @Test
    fun `clearing one symbol leaves every other symbol alone`() = runTest {
        val store = SymbolChartStateStore(FakeStatePreferences())
        store.put(gold().copy(symbol = "AAA", updatedAt = 1L))
        store.put(gold().copy(symbol = "BBB", updatedAt = 2L))

        store.clear("AAA")

        assertNull(store.state("AAA").first())
        assertEquals(listOf("BBB"), store.all().first().map(SymbolChartState::symbol))
    }

    @Test
    fun `clearing the last symbol removes the entry rather than leaving an empty string behind`() = runTest {
        val backing = FakeStatePreferences()
        val store = SymbolChartStateStore(backing)
        store.put(gold())

        store.clear("XAUUSD")

        assertNull(backing.data.first()[SymbolChartStateStore.STATES])
    }

    @Test
    fun `the two hundred and first symbol evicts the least recently updated one and keeps the rest`() = runTest {
        val store = SymbolChartStateStore(FakeStatePreferences())
        // Written oldest first, so the eviction cannot accidentally be "drop whatever came last".
        (1..SymbolChartStateStore.MAX_SYMBOLS).forEach { index ->
            store.put(gold().copy(symbol = "SYM$index", updatedAt = index.toLong()))
        }

        store.put(gold().copy(symbol = "NEWEST", updatedAt = 10_000L))

        val symbols = store.all().first().map(SymbolChartState::symbol)
        assertEquals(SymbolChartStateStore.MAX_SYMBOLS, symbols.size)
        assertEquals("NEWEST", symbols.first())
        // SYM1 carried the smallest updatedAt, so it is the one that goes.
        assertTrue("SYM1" !in symbols)
        assertTrue("SYM2" in symbols)
        assertTrue("SYM200" in symbols)
    }

    @Test
    fun `a short row written by an older build decodes with defaults instead of being discarded`() {
        // Two fields where this version writes eight: a symbol and a timeframe, and nothing else.
        val decoded = SymbolChartStateStore.decode("XAUUSD\u001EH1")

        assertEquals("XAUUSD", decoded?.symbol)
        assertEquals("H1", decoded?.timeframe)
        assertNull(decoded?.chartType)
        assertEquals(emptyList<String>(), decoded?.indicators)
        assertEquals(emptyMap<String, Int>(), decoded?.indicatorPeriods)
        assertEquals(false, decoded?.logScale)
        assertEquals(0L, decoded?.updatedAt)
    }

    @Test
    fun `a row with a trailing field this build has never heard of decodes rather than failing`() {
        val record = SymbolChartStateStore.encode(gold())!! + "\u001Efuture_field_from_a_newer_build"

        assertEquals(gold(), SymbolChartStateStore.decode(record))
    }

    @Test
    fun `a row with no symbol is dropped, because nothing can address it`() {
        assertNull(SymbolChartStateStore.decode("\u001EH1\u001ECANDLES"))
    }

    @Test
    fun `a period with no number drops that entry and keeps the indicators around it`() = runTest {
        // A half-written map: three parts where pairs are expected.
        val record = listOf("XAUUSD", "H1", "", "ema\u001Frsi", "ema\u001F21\u001Frsi", "", "0", "7")
            .joinToString("\u001E")
        val backing = FakeStatePreferences(mutablePreferencesOf(SymbolChartStateStore.STATES to record))

        val stored = SymbolChartStateStore(backing).state("XAUUSD").first()

        assertEquals(listOf("ema", "rsi"), stored?.indicators)
        assertEquals(mapOf("ema" to 21), stored?.indicatorPeriods)
        assertEquals(7L, stored?.updatedAt)
    }

    @Test
    fun `an indicator id carrying a separator is dropped without taking the symbol's row with it`() = runTest {
        val store = SymbolChartStateStore(FakeStatePreferences())
        store.put(gold().copy(indicators = listOf("ema", "bad\u001Eid")))

        val stored = store.state("XAUUSD").first()
        assertEquals(listOf("ema"), stored?.indicators)
        assertEquals("H4", stored?.timeframe)
    }
}

private class FakeStatePreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
