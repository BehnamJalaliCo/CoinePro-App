package com.coinepro.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget snapshot's encoding.
 *
 * Worth testing hard for a reason specific to widgets: this string is written by one process and
 * read by another, and when it goes wrong the reader does not see an exception — they see one
 * market's price sitting under another market's name, on their home screen, and they believe it.
 */
class WidgetSnapshotCodecTest {

    private val markets = listOf(
        WidgetMarket("BTC/USDT", "\u0628\u06CC\u062A\u200C\u06A9\u0648\u06CC\u0646", "92,140.50", "+1.24\u066A", 1),
        WidgetMarket("XAU/USD", "\u0637\u0644\u0627", "2,592.60", "\u22120.42\u066A", -1),
        WidgetMarket("US500", "S&P 500", "5,880.10", "", 0),
    )

    private fun roundTrip(list: List<WidgetMarket>): List<WidgetMarket> =
        WidgetSnapshotStore.decode(WidgetSnapshotStore.encode(list), "0", "0").markets

    @Test
    fun `a snapshot survives the round trip exactly`() {
        assertEquals(markets, roundTrip(markets))
    }

    @Test
    fun `Persian text, signs and empty fields all survive`() {
        // Every one of these has broken a delimited codec somewhere: right-to-left text, a real
        // minus sign rather than a hyphen, a per-mille sign, and a field that is legitimately empty.
        val awkward = listOf(
            WidgetMarket("EUR/USD", "\u06CC\u0648\u0631\u0648 / \u062F\u0644\u0627\u0631", "1.0842", "\u22120.03\u066A", -1),
            WidgetMarket("ETH/USDT", "", "", "", 0),
        )
        val decoded = roundTrip(awkward)
        assertEquals(awkward[0], decoded[0])
        // A nameless market comes back named after its ticker rather than blank. That is the
        // decoder's deliberate fallback and not a round-trip failure: a widget row with an empty
        // line where the name goes reads as a rendering bug, and the ticker is always true.
        assertEquals(awkward[1].copy(name = "ETH/USDT"), decoded[1])
    }

    @Test
    fun `a field carrying a separator is dropped rather than written`() {
        // The failure this prevents: a separator inside a field parses back as two fields and
        // shifts every field after it, so one market's price appears under another's name. Losing
        // the row is the correct trade — a widget with four markets instead of five is a small
        // failure and a widget that lies is not.
        val poisoned = markets + WidgetMarket(
            symbol = "BAD" + WidgetSnapshotStore.RECORD + "SYM",
            name = "x",
            priceText = "1",
            changeText = "",
            direction = 0,
        )
        assertEquals(markets, roundTrip(poisoned))
    }

    @Test
    fun `decoding never throws on rubbish`() {
        // A record from an older build, or half-written when the process died. The widget renders
        // what it can; it does not take the launcher's host process down with it.
        listOf(
            "",
            "   ",
            "onlyonefield",
            "a" + WidgetSnapshotStore.RECORD + "b",
            WidgetSnapshotStore.GROUP + WidgetSnapshotStore.GROUP,
            "a b",
        ).forEach { rubbish ->
            val snapshot = WidgetSnapshotStore.decode(rubbish, "not-a-number", "maybe")
            assertTrue("'$rubbish' produced markets", snapshot.markets.isEmpty())
            assertEquals(0L, snapshot.capturedAtEpochMillis)
            assertFalse(snapshot.stale)
        }
    }

    @Test
    fun `a half-written record is skipped and its neighbours survive`() {
        val good = WidgetSnapshotStore.encode(markets)
        val truncated = good + WidgetSnapshotStore.GROUP + "BTC/USDT" + WidgetSnapshotStore.RECORD + "bit"
        assertEquals(markets, WidgetSnapshotStore.decode(truncated, "0", "0").markets)
    }

    @Test
    fun `the stored list is capped`() {
        val many = (1..40).map { WidgetMarket("S$it", "n$it", "1", "", 0) }
        assertEquals(WidgetSnapshotStore.MAX_MARKETS, roundTrip(many).size)
    }

    @Test
    fun `an unreadable direction is flat rather than a guess at a colour`() {
        val record = listOf("BTC/USDT", "x", "1", "", "up").joinToString(WidgetSnapshotStore.RECORD)
        assertEquals(0, WidgetSnapshotStore.decode(record, "0", "0").markets.single().direction)
    }

    @Test
    fun `a direction outside minus one to one is clamped`() {
        val record = listOf("BTC/USDT", "x", "1", "", "97").joinToString(WidgetSnapshotStore.RECORD)
        assertEquals(1, WidgetSnapshotStore.decode(record, "0", "0").markets.single().direction)
    }

    @Test
    fun `the capture time and the stale flag round trip`() {
        val snapshot = WidgetSnapshotStore.decode(
            WidgetSnapshotStore.encode(markets),
            "1735000000000",
            "1",
        )
        assertEquals(1_735_000_000_000L, snapshot.capturedAtEpochMillis)
        assertTrue(snapshot.stale)
        assertFalse(snapshot.isEmpty)
    }
}
