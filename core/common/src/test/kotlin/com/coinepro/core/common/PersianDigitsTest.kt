package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianDigitsTest {
    @Test
    fun `persian and arabic digits fold to latin so a typed national id is not stripped away`() {
        assertEquals("0012345678", "۰۰۱۲۳۴۵۶۷۸".foldDigitsToLatin())
        assertEquals("0987654321", "٠٩٨٧٦٥٤٣٢١".foldDigitsToLatin())
        assertEquals("+989121234567", "+۹۸۹۱۲۱۲۳۴۵۶۷".foldDigitsToLatin())
        assertEquals("already-latin-1", "already-latin-1".foldDigitsToLatin())
    }

    @Test
    fun `the two digit families mix, because a phone keyboard switches mid-entry`() {
        assertEquals("1234", "۱٢۳٤".foldDigitsToLatin())
    }

    @Test
    fun `nothing but digits is touched, so persian letters and punctuation survive`() {
        assertEquals("کد 12: ok", "کد ۱۲: ok".foldDigitsToLatin())
        assertEquals("", "".foldDigitsToLatin())
    }

    @Test
    fun `a large prose count groups with the Arabic separator, not a comma`() {
        // U+066C, not ','. A Latin comma between Persian digits reads as a decimal point to
        // roughly half the world, which turns fifty-two thousand members into fifty-two.
        assertEquals("۵۲٬۳۴۰", 52_340L.toPersianGroupedDigits())
    }

    @Test
    fun `counts below a thousand carry no separator`() {
        assertEquals("۹۹۹", 999L.toPersianGroupedDigits())
        assertEquals("۰", 0L.toPersianGroupedDigits())
    }

    @Test
    fun `grouping starts from the right, whatever the length`() {
        assertEquals("۱٬۰۰۰", 1_000L.toPersianGroupedDigits())
        assertEquals("۱۲٬۳۴۵٬۶۷۸", 12_345_678L.toPersianGroupedDigits())
    }
}