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
}
