package com.coinepro.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiTextTest {

    @Test
    fun `an English sentence is a paragraph, not a run`() {
        // The case that produced «.lifting precious metals» on every news card the moment stories
        // started arriving from an English wire.
        assertTrue(BidiText.isLatinSentence("Treasury yields eased after the latest labour print."))
        assertTrue(BidiText.isLatinSentence("Spot Bitcoin ETFs post a fourth straight session"))
    }

    @Test
    fun `Persian copy is not`() {
        assertFalse(BidiText.isLatinSentence("ریزش ۱۲ درصدی بهره باز بیت‌کوین"))
        assertFalse(BidiText.isLatinSentence("طلا از سقف کانال روزانه برگشت"))
    }

    @Test
    fun `Persian carrying a ticker is still Persian`() {
        // A run inside a paragraph. `isolateLtr` is what that wants; changing the paragraph's
        // direction for it would turn one correct sentence into one broken one.
        assertFalse(BidiText.isLatinSentence("قیمت BTC به ۹۱٬۲۴۸ رسید"))
        assertFalse(BidiText.isLatinSentence("تحلیل XAUUSD در تایم‌فریم H1"))
    }

    @Test
    fun `figures and punctuation decide nothing`() {
        // Direction-neutral. Counting them would call «۱۲ BTC» Latin, and a bare number belongs to
        // whatever paragraph it is standing in.
        assertFalse(BidiText.isLatinSentence("91,248.30"))
        assertFalse(BidiText.isLatinSentence("+1.84%"))
        assertFalse(BidiText.isLatinSentence("— · —"))
        assertFalse(BidiText.isLatinSentence(""))
    }

    @Test
    fun `an isolate survives being stripped`() {
        val value = "XAUUSD"
        assertTrue(BidiText.isolateLtr(value).length > value.length)
        org.junit.Assert.assertEquals(value, BidiText.strip(BidiText.isolateLtr(value)))
    }
}
