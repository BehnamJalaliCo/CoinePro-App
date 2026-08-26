package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketNumberFormatterTest {

    /**
     * The bug this exists to prevent: a crypto list holds prices four orders of magnitude apart,
     * and a fixed two decimals turns the small ones into 0.00 — not a rounding but a claim that the
     * asset is worthless.
     */
    @Test
    fun `decimals follow the price's own magnitude`() {
        assertEquals("64,182.40", plain(MarketNumberFormatter.priceAuto(64_182.4)))
        assertEquals("148.92", plain(MarketNumberFormatter.priceAuto(148.92)))
        assertEquals("1.00", plain(MarketNumberFormatter.priceAuto(1.0)))
        assertEquals("0.5241", plain(MarketNumberFormatter.priceAuto(0.5241)))
        assertEquals("0.0100", plain(MarketNumberFormatter.priceAuto(0.01)))
        assertEquals("0.001234", plain(MarketNumberFormatter.priceAuto(0.001234)))
        assertEquals("0.00002418", plain(MarketNumberFormatter.priceAuto(0.00002418)))
    }

    @Test
    fun `magnitude is taken unsigned, so a negative gets the decimals its size deserves`() {
        assertEquals("-0.5241", plain(MarketNumberFormatter.priceAuto(-0.5241)))
    }

    @Test
    fun `zero is a whole-unit price, not an eight-decimal one`() {
        assertEquals("0.00", plain(MarketNumberFormatter.priceAuto(0.0)))
    }

    /**
     * Every price leaves here inside one left-to-right isolate. Without it a Latin number in a
     * Persian column reorders, and 64,182.40 is read out in the wrong order by the very readers
     * this app is for.
     */
    @Test
    fun `the output is isolated exactly once`() {
        val formatted = MarketNumberFormatter.priceAuto(64_182.4)
        assertEquals(1, formatted.count { it == '⁦' })
        assertEquals(1, formatted.count { it == '⁩' })
    }
}

private fun plain(value: String) = BidiText.strip(value)
