package com.coinepro.feature.copytrade

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The link's state, as a reader meets it.
 *
 * It was the server's raw word for a long time, on the argument that support would ask for it back.
 * That is a good reason to keep the raw word *reachable* and a poor one to make it the most
 * prominent thing on an otherwise Persian card — «connected» and «disconnected» are one glance
 * apart to somebody who reads English and indistinguishable to somebody who does not.
 */
class CopyStatusLabelTest {

    @Test
    fun `the states a reader meets are Persian`() {
        assertEquals("متصل", copyStatusLabel("connected"))
        assertEquals("قطع", copyStatusLabel("disconnected"))
        assertEquals("در حال اتصال", copyStatusLabel("pending"))
    }

    @Test
    fun `case and separators do not make a new state`() {
        assertEquals("اطلاعات ورود پذیرفته نشد", copyStatusLabel("INVALID_CREDENTIALS"))
        assertEquals("اطلاعات ورود پذیرفته نشد", copyStatusLabel("invalid-credentials"))
    }

    @Test
    fun `a word nobody wrote down arrives as the server said it`() {
        // Readable rather than guessed at: support can still ask for it and get something that
        // matches their own logs.
        assertEquals("margin call", copyStatusLabel("margin_call"))
    }

    @Test
    fun `no status is no line`() {
        assertEquals("", copyStatusLabel(null))
        assertEquals("", copyStatusLabel("  "))
    }
}
