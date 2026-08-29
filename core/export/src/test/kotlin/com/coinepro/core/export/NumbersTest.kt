package com.coinepro.core.export

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The numeric rule, checked on the machine it exists for.
 *
 * A device left to its own locale here is Persian, and a number rendered by it is «۲۴۱۲٫۸۵» — text
 * that a spreadsheet sums to zero while displaying something that looks exactly right.
 */
class NumbersTest {

    private lateinit var previous: Locale

    @Before
    fun useAPersianDevice() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fa-IR"))
    }

    @After
    fun restoreTheDevice() {
        Locale.setDefault(previous)
    }

    @Test
    fun `a cell holds Latin digits and a dot however the device is set`() {
        assertEquals("fa", Locale.getDefault().language)
        val cell = Numbers.cell(2_412.85)
        assertEquals("2412.85", cell)
        assertTrue(cell.none { it in '۰'..'۹' || it in '٠'..'٩' || it == '٫' })
    }

    @Test
    fun `a large number carries no thousands separator`() {
        // `64,182.4` inside a cell is a string, and in a CSV it is also a column boundary hiding
        // inside a field.
        assertEquals("64182.4", Numbers.cell(64_182.4))
    }

    @Test
    fun `a price keeps its precision without inventing any`() {
        assertEquals("0.00000042", Numbers.cell(0.00000042))
        assertEquals("2643.17", Numbers.cell(2643.17))
        assertEquals("280", Numbers.cell(280.0))
    }

    @Test
    fun `an absent or non-finite value leaves the cell empty rather than writing a zero`() {
        assertEquals("", Numbers.cell(null))
        assertEquals("", Numbers.cell(Double.NaN))
        // The profit factor of a run with no losing trade. «∞» in a numeric column is text a
        // spreadsheet sums as zero while showing it to the reader as an answer.
        assertEquals("", Numbers.cell(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `a cell reads back as the number it was written from`() {
        assertEquals(-124.5, Numbers.parse(Numbers.cell(-124.5))!!, 1e-9)
        assertNull(Numbers.parse(""))
        assertNull(Numbers.parse("خرید"))
        // Persian digits are text here too: a lenient parse would be the type-guessing this module
        // exists to remove.
        assertNull(Numbers.parse("۱۲"))
    }
}
