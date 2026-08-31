package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The Persian zero is a dot, so a middle dot next to a numeral is a different number.
 *
 * Found on the journal's tag cloud: three entries, one tag each, and every chip read «شکست ۱۰».
 */
class PersianCountTest {

    @Test
    fun `a count of one does not read as ten`() {
        assertEquals("شکست (۱)", countedLabel("شکست", 1))
        assertFalse("the separator that caused this is gone", "·" in countedLabel("شکست", 1))
    }

    @Test
    fun `the digits are Persian, because a count of things is prose`() {
        assertEquals("برگشت (۱۲)", countedLabel("برگشت", 12))
        assertEquals("خالی (۰)", countedLabel("خالی", 0))
    }
}
