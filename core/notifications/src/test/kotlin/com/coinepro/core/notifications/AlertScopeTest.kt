package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * What a watchlist alert covers, and when it decides.
 *
 * The whole promise of a watchlist alert is that it keeps up with the list. A reader who stars a
 * new coin expects it to be covered without going back to the alerts screen, and one who unstars a
 * coin expects to stop hearing about it. Both of those are the same property — membership is
 * resolved at evaluation, never captured — and both are asserted below.
 */
class AlertScopeTest {

    private val watchlist = AlertScope.Watchlist("main")

    @Test
    fun `a symbol scope covers exactly its own ticker`() {
        assertEquals(listOf("BTCUSDT"), AlertScope.Symbol("BTCUSDT").resolve { emptyList() })
    }

    @Test
    fun `a watchlist scope covers whatever the list holds at the moment it is asked`() {
        var members = listOf("BTCUSDT", "ETHUSDT")
        val lookup: (String) -> List<String> = { id -> if (id == "main") members else emptyList() }

        assertEquals(listOf("BTCUSDT", "ETHUSDT"), watchlist.resolve(lookup))

        members = members + "SOLUSDT"
        assertEquals(listOf("BTCUSDT", "ETHUSDT", "SOLUSDT"), watchlist.resolve(lookup))

        members = members - "BTCUSDT"
        assertEquals(listOf("ETHUSDT", "SOLUSDT"), watchlist.resolve(lookup))
    }

    /** A list that has since been deleted covers nothing. It does not delete the alert. */
    @Test
    fun `a watchlist that no longer exists covers nothing`() {
        assertEquals(emptyList<String>(), watchlist.resolve { emptyList() })
    }

    @Test
    fun `an alert with no scope of its own falls back to its single symbol`() {
        val alert = LocalPriceAlert(
            id = "a",
            symbol = "BTCUSDT",
            condition = LocalAlertCondition.ABOVE,
            value = 65_000.0,
        )
        assertEquals(AlertScope.Symbol("BTCUSDT"), alert.effectiveScope)
        assertEquals(listOf("BTCUSDT"), alert.symbols { emptyList() })
        assertEquals(
            listOf("ETHUSDT"),
            alert.copy(scope = watchlist).symbols { listOf("ETHUSDT") },
        )
    }

    @Test
    fun `a scope with no name is refused`() {
        assertThrows(IllegalArgumentException::class.java) { AlertScope.Symbol("  ") }
        assertThrows(IllegalArgumentException::class.java) { AlertScope.Watchlist("") }
    }

    @Test
    fun `a scope survives a round trip and an unreadable one decodes to null`() {
        listOf(AlertScope.Symbol("BTCUSDT"), watchlist).forEach { scope ->
            assertEquals(scope, AlertScope.decode(AlertScope.encode(scope)))
        }
        assertEquals("", AlertScope.encode(null))
        assertNull(AlertScope.decode(null))
        assertNull(AlertScope.decode(""))
        assertNull(AlertScope.decode("symbol"))
        assertNull(AlertScope.decode("portfolio\u001FBTCUSDT"))
    }
}
