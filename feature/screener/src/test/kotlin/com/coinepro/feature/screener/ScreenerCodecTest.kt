package com.coinepro.feature.screener

import com.coinepro.feature.screener.model.NumericOp
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerIndicatorId
import com.coinepro.feature.screener.model.ScreenerScreen
import com.coinepro.feature.screener.model.ScreenerSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved-screen encoding, tested without a `DataStore`.
 *
 * The properties that matter are the ones a reader would notice only after losing work: a screen
 * comes back exactly as it went in, a condition this build cannot read costs one condition rather
 * than the whole screen, and a name carrying a separator is refused rather than written into a
 * record that would parse back as different fields.
 */
class ScreenerCodecTest {

    private val screen = ScreenerScreen(
        id = "screen_1",
        name = "روند قوی",
        filters = listOf(
            ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.BETWEEN, 2.0, 5.5),
            ScreenerFilter.Category(ScreenerField.ASSET_CLASS, setOf("CRYPTO", "METAL")),
            ScreenerFilter.TextMatch("طلا"),
            ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, 2, NumericOp.LT, 12.5),
        ),
        sort = ScreenerSort(ScreenerField.VOLUME, descending = false),
        columns = listOf(ScreenerField.LAST_PRICE, ScreenerField.RSI, ScreenerField.QUOTE_VOLUME),
    )

    @Test
    fun `a screen survives a round trip with every kind of condition on it`() {
        val restored = ScreenerCodec.decode(ScreenerCodec.encode(screen)!!)
        assertEquals(screen, restored)
    }

    @Test
    fun `a range keeps both of its bounds`() {
        // The bound is the field most easily lost, because every other operator ignores it.
        val restored = ScreenerCodec.decode(ScreenerCodec.encode(screen)!!)!!
        val range = restored.filters.filterIsInstance<ScreenerFilter.Numeric>().single()
        assertEquals(2.0, range.value, 1e-9)
        assertEquals(5.5, range.bound!!, 1e-9)
    }

    @Test
    fun `several screens round trip as a list, in the order they were saved`() {
        val second = screen.copy(id = "screen_2", name = "اشباع فروش")
        val encoded = ScreenerCodec.encodeAll(listOf(screen, second))
        assertEquals(listOf(screen, second), ScreenerCodec.decodeAll(encoded))
    }

    @Test
    fun `nothing stored decodes to no screens rather than to one empty one`() {
        assertTrue(ScreenerCodec.decodeAll("").isEmpty())
    }

    @Test
    fun `a name carrying a separator is refused rather than silently rewritten`() {
        // Sanitising would rename somebody's screen without telling them, which is worse than
        // declining to save it.
        assertNull(ScreenerCodec.encode(screen.copy(name = "a\u001Eb")))
        assertNull(ScreenerCodec.encode(screen.copy(id = "a\u001Fb")))
        assertNull(ScreenerCodec.encode(screen.copy(name = "   ")))
    }

    @Test
    fun `a condition this build cannot read costs one condition, not the screen`() {
        val encoded = ScreenerCodec.encode(screen)!!
        // What a later build would write: a filter tag this version has never heard of.
        val withUnknown = encoded + "\u001E" + "z\u001Fsomething\u001F9"
        val restored = ScreenerCodec.decode(withUnknown)!!
        assertEquals(screen.name, restored.name)
        assertEquals(screen.filters.size, restored.filters.size)
    }

    @Test
    fun `an operator this build does not have drops only its own condition`() {
        val encoded = ScreenerCodec.encode(
            screen.copy(filters = listOf(ScreenerFilter.TextMatch("طلا"))),
        )!!
        val withUnknownOperator = encoded + "\u001E" + "n\u001FCHANGE_PERCENT\u001FNEARLY\u001F3.0\u001F"
        val restored = ScreenerCodec.decode(withUnknownOperator)!!
        assertEquals(listOf(ScreenerFilter.TextMatch("طلا")), restored.filters)
    }

    @Test
    fun `a record with no id is dropped, because nothing can address it`() {
        assertNull(ScreenerCodec.decode(""))
        assertNull(ScreenerCodec.decode("\u001Dنام"))
    }

    @Test
    fun `a short record from an older build takes the defaults for what it is missing`() {
        val restored = ScreenerCodec.decode("screen_9\u001Dدیده‌بان")!!
        assertEquals("screen_9", restored.id)
        assertEquals("دیده‌بان", restored.name)
        assertEquals(ScreenerSort.DEFAULT, restored.sort)
        assertEquals(ScreenerField.DEFAULT_COLUMNS, restored.columns)
        assertTrue(restored.filters.isEmpty())
    }

    @Test
    fun `a column this build does not have is dropped without emptying the column list`() {
        val encoded = ScreenerCodec.encode(
            screen.copy(columns = listOf(ScreenerField.LAST_PRICE, ScreenerField.VOLUME)),
        )!!
        val restored = ScreenerCodec.decode(encoded.replace("VOLUME", "SHARPE_RATIO"))!!
        assertEquals(listOf(ScreenerField.LAST_PRICE), restored.columns)
    }
}
