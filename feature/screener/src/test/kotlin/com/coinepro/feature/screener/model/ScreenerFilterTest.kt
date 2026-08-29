package com.coinepro.feature.screener.model

import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One test per filter type, all of them about the boundary.
 *
 * The middle of a range is never where a filter is wrong. What breaks is the edge — whether five is
 * in "between two and five", whether a change of exactly zero counts as a rise, whether a market
 * whose volume has not arrived is shown or hidden — and every one of those is a decision this suite
 * pins so it cannot drift.
 */
class ScreenerFilterTest {

    private fun row(
        symbol: String = "BTCUSDT",
        price: Double? = 100.0,
        changePercent: Double? = null,
        volume: Double? = null,
        indicators: Map<String, Double> = emptyMap(),
    ) = ScreenerRow(
        meta = SymbolClassifier.classify(symbol),
        price = price,
        changePercent = changePercent,
        volume = volume,
        indicators = indicators,
        market = "CRYPTO",
    )

    // ── Numeric ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `greater than excludes the boundary and greater or equal includes it`() {
        val at = row(changePercent = 3.0)
        assertFalse(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, 3.0).matches(at))
        assertTrue(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GTE, 3.0).matches(at))
    }

    @Test
    fun `less than excludes the boundary and less or equal includes it`() {
        val at = row(changePercent = -2.5)
        assertFalse(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.LT, -2.5).matches(at))
        assertTrue(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.LTE, -2.5).matches(at))
    }

    @Test
    fun `equality on a double uses a delta rather than an exact comparison`() {
        // (2.6 - 2.5) / 2.5 * 100 is 4.000000000000001 in binary floating point. A reader who types
        // 4 and is told nothing matched has been failed by the machine, not by the market.
        val computed = (2.6 - 2.5) / 2.5 * 100.0
        assertTrue("the arithmetic really is inexact", computed != 4.0)
        assertTrue(
            ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.EQ, 4.0)
                .matches(row(changePercent = computed)),
        )
    }

    @Test
    fun `equality still refuses a number that is genuinely different`() {
        assertFalse(
            ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.EQ, 4.0)
                .matches(row(changePercent = 4.01)),
        )
    }

    @Test
    fun `equality scales its delta with the magnitude being compared`() {
        // A relative epsilon is the point: at ninety thousand the gap between representable doubles
        // is already larger than a fixed 1e-9 would allow.
        val price = 91_248.30
        val drifted = price + price * 1e-12
        assertTrue(
            ScreenerFilter.Numeric(ScreenerField.LAST_PRICE, NumericOp.EQ, price)
                .matches(row(price = drifted)),
        )
    }

    @Test
    fun `between is inclusive at both ends`() {
        val filter = ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.BETWEEN, 2.0, 5.0)
        assertTrue("the lower bound is in", filter.matches(row(changePercent = 2.0)))
        assertTrue("the upper bound is in", filter.matches(row(changePercent = 5.0)))
        assertTrue(filter.matches(row(changePercent = 3.5)))
        assertFalse(filter.matches(row(changePercent = 1.9999)))
        assertFalse(filter.matches(row(changePercent = 5.0001)))
    }

    @Test
    fun `between orders its bounds, so a range control's handles may be dragged either way`() {
        val reversed = ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.BETWEEN, 5.0, 2.0)
        assertTrue(reversed.matches(row(changePercent = 3.5)))
        assertTrue(reversed.matches(row(changePercent = 2.0)))
        assertTrue(reversed.matches(row(changePercent = 5.0)))
    }

    @Test
    fun `between with no second bound is a range of zero width, which is equality`() {
        val degenerate = ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.BETWEEN, 4.0)
        assertTrue(degenerate.matches(row(changePercent = 4.0)))
        assertFalse(degenerate.matches(row(changePercent = 4.5)))
    }

    @Test
    fun `a market whose figure has not arrived is not a match, in either direction`() {
        // Null is "not read yet", never zero. Both of these would pass if null were treated as 0.0,
        // and the table would fill with markets that vanish one by one as the bars land.
        val unresolved = row(changePercent = null)
        assertFalse(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, -1.0).matches(unresolved))
        assertFalse(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.LT, 1.0).matches(unresolved))
    }

    @Test
    fun `a numeric threshold on a categorical field matches nothing rather than crashing`() {
        // Unreachable from the sheet, but a saved screen written by a later build could carry one.
        assertFalse(ScreenerFilter.Numeric(ScreenerField.ASSET_CLASS, NumericOp.GT, 0.0).matches(row()))
    }

    // ── Category ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an empty category set matches everything, not nothing`() {
        // The documented choice. The set is what the reader has ticked, and a freshly opened sheet
        // has nothing ticked; reading that as "allow none" would empty the table before the reader
        // touched anything, and the only way back would be to tick every chip.
        val filter = ScreenerFilter.Category(ScreenerField.ASSET_CLASS, emptySet())
        assertTrue(filter.matches(row("BTCUSDT")))
        assertTrue(filter.matches(row("EURUSD")))
        assertTrue(filter.matches(row("XAUUSD")))
    }

    @Test
    fun `a category set keeps only the classes it names`() {
        val filter = ScreenerFilter.Category(ScreenerField.ASSET_CLASS, setOf("CRYPTO"))
        assertTrue(filter.matches(row("BTCUSDT")))
        assertFalse(filter.matches(row("EURUSD")))
    }

    @Test
    fun `a category set is compared case-insensitively`() {
        val filter = ScreenerFilter.Category(ScreenerField.QUOTE_CURRENCY, setOf("usdt"))
        assertTrue(filter.matches(row("BTCUSDT")))
    }

    @Test
    fun `a market with no value for the field is excluded by a non-empty set`() {
        // An index has no quote leg. Inventing USD for it would put it inside a filter a reader
        // built specifically to exclude it.
        val filter = ScreenerFilter.Category(ScreenerField.QUOTE_CURRENCY, setOf("USD"))
        assertFalse(filter.matches(row("US500")))
    }

    // ── TextMatch ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a text condition matches the Persian name, which a substring filter never would`() {
        // Routed through core:symbols' matcher. `"BTCUSDT".contains("بیت‌کوین")` is false, and that
        // is exactly the bug this filter exists not to have.
        val filter = ScreenerFilter.TextMatch("بیت")
        assertTrue(filter.matches(row("BTCUSDT")))
        assertFalse(filter.matches(row("EURUSD")))
    }

    @Test
    fun `a text condition matches a ticker regardless of case`() {
        assertTrue(ScreenerFilter.TextMatch("btc").matches(row("BTCUSDT")))
    }

    @Test
    fun `a blank text condition matches everything, because an empty box is not a search`() {
        assertTrue(ScreenerFilter.TextMatch("   ").matches(row("EURUSD")))
    }

    // ── IndicatorFilter ─────────────────────────────────────────────────────────────────────

    @Test
    fun `an indicator condition reads the key its own period addresses`() {
        val filter = ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, period = 2, op = NumericOp.LT, value = 10.0)
        assertEquals("rsi:2", filter.key)
        assertTrue(filter.matches(row(indicators = mapOf("rsi:2" to 8.0))))
        // The fourteen-bar reading is a different question and must not answer this one.
        assertFalse(filter.matches(row(indicators = mapOf("rsi:14" to 8.0))))
    }

    @Test
    fun `an indicator condition with no period falls back to the field's own default`() {
        val filter = ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, period = null, op = NumericOp.LT, value = 30.0)
        assertEquals("rsi:14", filter.key)
        assertTrue(filter.matches(row(indicators = mapOf("rsi:14" to 22.0))))
    }

    @Test
    fun `an indicator condition boundary follows the operator exactly`() {
        val row = row(indicators = mapOf("rsi:14" to 30.0))
        assertFalse(ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, 14, NumericOp.LT, 30.0).matches(row))
        assertTrue(ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, 14, NumericOp.LTE, 30.0).matches(row))
    }

    @Test
    fun `a market whose indicator has not been computed is not a match`() {
        val filter = ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, 14, NumericOp.LT, 30.0)
        assertFalse(filter.matches(row(indicators = emptyMap())))
    }

    // ── conjunction ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `every condition has to pass, and an empty condition list passes everything`() {
        val subject = row(changePercent = 4.0, indicators = mapOf("rsi:14" to 65.0))
        assertTrue(ScreenerFilter.allMatch(emptyList(), subject))
        assertTrue(
            ScreenerFilter.allMatch(
                listOf(
                    ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, 0.0),
                    ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, 14, NumericOp.LT, 70.0),
                ),
                subject,
            ),
        )
        assertFalse(
            ScreenerFilter.allMatch(
                listOf(
                    ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, 0.0),
                    ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, 14, NumericOp.LT, 60.0),
                ),
                subject,
            ),
        )
    }

    @Test
    fun `the indicator readings a screen needs are collected from both filters and fields`() {
        val keys = ScreenerFilter.indicatorKeys(
            listOf(
                ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, 2, NumericOp.LT, 10.0),
                ScreenerFilter.Numeric(ScreenerField.ADX, NumericOp.GT, 25.0),
                ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, 0.0),
            ),
        )
        assertEquals(setOf("rsi:2", "adx:14"), keys)
    }
}
