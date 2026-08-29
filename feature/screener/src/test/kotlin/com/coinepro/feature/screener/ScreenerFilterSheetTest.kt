package com.coinepro.feature.screener

import com.coinepro.core.common.BidiText
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.feature.screener.model.NumericOp
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerIndicatorId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The judgement inside the filter sheet, extracted so it can be checked without a device.
 *
 * Three decisions live here and none of them is obvious from the screen: a condition on a derived
 * field builds the indicator filter rather than a plain threshold, the category chips and the text
 * box write into the *same* filter list the sheet edits rather than keeping their own copy, and a
 * cleared control removes its condition instead of leaving behind one that matches everything.
 */
class ScreenerFilterSheetTest {

    /** The isolates around each Latin figure, removed so an expected sentence reads in a diff. */
    private fun plain(value: String) = BidiText.strip(value)

    // ── buildFilter ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a condition on a plain field is an ordinary threshold`() {
        val filter = buildFilter(ScreenerField.CHANGE_PERCENT, NumericOp.GT, "3", "", "")
        assertEquals(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, 3.0), filter)
    }

    @Test
    fun `a condition on a derived field is the indicator filter, which is the free one`() {
        // [109]. Choosing «شاخص قدرت نسبی» in the sheet has to produce a filter that carries its own
        // lookback, or a reader could never ask two questions about the same indicator at once.
        val filter = buildFilter(ScreenerField.RSI, NumericOp.LT, "30", "", "2")
        assertEquals(
            ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, 2, NumericOp.LT, 30.0),
            filter,
        )
    }

    @Test
    fun `an empty period box takes the field's own default rather than no period at all`() {
        val filter = buildFilter(ScreenerField.RSI, NumericOp.LT, "30", "", "") as ScreenerFilter.IndicatorFilter
        assertEquals(14, filter.period)
        assertEquals("rsi:14", filter.key)
    }

    @Test
    fun `a range with only one number typed is not yet a condition`() {
        assertNull(buildFilter(ScreenerField.CHANGE_PERCENT, NumericOp.BETWEEN, "2", "", ""))
        assertEquals(
            ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.BETWEEN, 2.0, 5.0),
            buildFilter(ScreenerField.CHANGE_PERCENT, NumericOp.BETWEEN, "2", "5", ""),
        )
    }

    @Test
    fun `a half-typed number is nothing rather than an error`() {
        assertNull(buildFilter(ScreenerField.CHANGE_PERCENT, NumericOp.GT, "", "", ""))
        assertNull(buildFilter(ScreenerField.CHANGE_PERCENT, NumericOp.GT, "-", "", ""))
    }

    // ── the chips and the search box ────────────────────────────────────────────────────────

    @Test
    fun `choosing a category writes one condition and choosing another replaces it`() {
        val crypto = withCategory(emptyList(), SymbolCategory.CRYPTO)
        assertEquals(
            listOf(ScreenerFilter.Category(ScreenerField.ASSET_CLASS, setOf("CRYPTO"))),
            crypto,
        )
        val forex = withCategory(crypto, SymbolCategory.FOREX)
        assertEquals(
            listOf(ScreenerFilter.Category(ScreenerField.ASSET_CLASS, setOf("FOREX"))),
            forex,
        )
    }

    @Test
    fun `clearing the category removes the condition rather than leaving an empty one`() {
        // An empty set matches everything by design, so leaving one behind would show the reader a
        // condition row that does nothing and would make the screen claim to be filtered.
        val cleared = withCategory(withCategory(emptyList(), SymbolCategory.CRYPTO), null)
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `the chip row reads its state back out of the filter list`() {
        assertNull(selectedCategory(emptyList()))
        assertEquals(
            SymbolCategory.METAL,
            selectedCategory(withCategory(emptyList(), SymbolCategory.METAL)),
        )
    }

    @Test
    fun `a category condition other filters wrote is left alone`() {
        val other = ScreenerFilter.Numeric(ScreenerField.VOLUME, NumericOp.GT, 1.0)
        val result = withCategory(listOf(other), SymbolCategory.CRYPTO)
        assertEquals(other, result.first())
        assertEquals(2, result.size)
    }

    @Test
    fun `the search box writes one text condition and clears it when emptied`() {
        val searching = withTextMatch(emptyList(), "طلا")
        assertEquals(listOf(ScreenerFilter.TextMatch("طلا")), searching)
        assertEquals("طلا", textQuery(searching))
        assertTrue(withTextMatch(searching, "  ").isEmpty())
        assertEquals("", textQuery(emptyList()))
    }

    // ── the sentence a condition row reads as ───────────────────────────────────────────────

    @Test
    fun `a condition reads as a sentence with Persian words and Latin numbers`() {
        assertEquals(
            "تغییر روزانه بیشتر از 3",
            plain(describe(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, 3.0))),
        )
    }

    @Test
    fun `an indicator condition spells its period out, so two of them can be told apart`() {
        assertEquals(
            "شاخص قدرت نسبی 2 کمتر از 10",
            plain(describe(ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, 2, NumericOp.LT, 10.0))),
        )
    }

    @Test
    fun `a range reads as both of its ends`() {
        assertEquals(
            "تغییر روزانه بین 2 — 5",
            plain(describe(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.BETWEEN, 2.0, 5.0))),
        )
    }
}
