package com.coinepro.core.marketdata

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketSearchControllerTest {

    private val universe = listOf(
        "BTCUSDT", "ETHUSDT", "SOLUSDT", "AAVEUSDT", "PEPEUSDT",
        "EURUSD", "GBPUSD", "XAUUSD", "US500",
    )

    private fun gateway(symbols: List<String> = universe) = object : MarketCatalogGateway {
        override suspend fun load(): MarketCatalog = MarketCatalog(
            markets = symbols.map(SymbolClassifier::classify),
            quotes = emptyMap(),
            serverTimeEpochMillis = null,
        )
    }

    @Test
    fun `the catalogue is whatever the server quotes, not a constant`() = runTest {
        val controller = MarketSearchController(gateway(), this)
        controller.start()
        advanceUntilIdle()
        assertEquals(universe.size, controller.state.value.catalogSize)
        assertEquals(universe.size, controller.state.value.results.size)
    }

    @Test
    fun `typing is debounced and the empty box is not`() = runTest {
        val controller = MarketSearchController(gateway(), this)
        controller.start()
        advanceUntilIdle()

        controller.setQuery("b")
        controller.setQuery("bt")
        controller.setQuery("btc")
        advanceUntilIdle()
        assertEquals("BTCUSDT", controller.state.value.results.first().meta.symbol)

        // Clearing must restore the browse list at once — a debounce here reads as a stuck screen.
        controller.setQuery("")
        assertEquals(universe.size, controller.state.value.results.size)
    }

    @Test
    fun `a category chip filters immediately`() = runTest {
        val controller = MarketSearchController(gateway(), this)
        controller.start()
        advanceUntilIdle()

        controller.setCategory(SymbolCategory.CRYPTO)
        // No advanceUntilIdle: a deliberate tap has no more input coming and must not wait.
        val results = controller.state.value.results
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.meta.category == SymbolCategory.CRYPTO })
    }

    @Test
    fun `a Persian name reaches the market`() = runTest {
        val controller = MarketSearchController(gateway(), this)
        controller.start()
        advanceUntilIdle()
        controller.setQuery("سولانا")
        advanceUntilIdle()
        assertEquals("SOLUSDT", controller.state.value.results.single().meta.symbol)
    }

    @Test
    fun `no result is an empty state rather than the whole catalogue`() = runTest {
        val controller = MarketSearchController(gateway(), this)
        controller.start()
        advanceUntilIdle()
        controller.setQuery("zzzz")
        advanceUntilIdle()
        assertTrue(controller.state.value.empty)
        assertTrue(controller.state.value.results.isEmpty())
    }

    @Test
    fun `a failed load says so in this app's own words, not the exception's`() = runTest {
        val failing = object : MarketCatalogGateway {
            override suspend fun load(): MarketCatalog =
                throw IllegalStateException("Unable to resolve host \"api.example\"")
        }
        val controller = MarketSearchController(failing, this)
        controller.start()
        advanceUntilIdle()

        // This assertion used to be `assertEquals("no route", state.error)` — it pinned the bug
        // in place. The exception's own message is an English platform string a reader can do
        // nothing with, and the markets screen printed it as product copy.
        assertEquals(UiMessage.of(MessageKey.MARKETS_UNAVAILABLE), controller.state.value.error)
        assertFalse(controller.state.value.loading)
    }
}
