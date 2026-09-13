package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every number in a Persian sentence, inside an isolate (run Ω-FIX item 6).
 *
 * The failure this closes is one line from the owner's device: «.در 11% از 9 بار درست بوده» — a
 * Persian sentence with two Latin runs in it and its own full stop moved to the front of the line.
 * What is asserted here is not the rendering, which no unit test can see, but the *marks*: the
 * paragraph has nothing left to resolve once each number is isolated, and the marks are countable.
 */
class BidiIsolateNumbersTest {

    private fun marks(value: String) = value.count { it == BidiText.FSI }

    @Test
    fun `every number in a sentence is isolated`() {
        val isolated = BidiText.isolateNumbers("در 11% از 9 بار درست بوده، با اندازه‌گیری 5 کندل بعد.")
        assertEquals("one isolate per figure", 3, marks(isolated))
        assertEquals("and one pop for each", 3, isolated.count { it == BidiText.PDI })
    }

    @Test
    fun `the per-cent sign goes inside the isolate with its figure`() {
        val isolated = BidiText.isolateNumbers("در 11% از 9 بار")
        assertTrue("the sign was left outside: $isolated", "${BidiText.FSI}11%${BidiText.PDI}" in isolated)
    }

    @Test
    fun `a sentence's own full stop stays outside`() {
        val isolated = BidiText.isolateNumbers("روی 91,263.03 است.")
        // The comma and the decimal point belong to the price; the one at the end belongs to the
        // sentence, and pulling it inside is the same bug pointing the other way.
        assertTrue("the price came apart: $isolated", "${BidiText.FSI}91,263.03${BidiText.PDI}" in isolated)
        assertTrue("the sentence lost its stop: $isolated", isolated.endsWith("است."))
    }

    @Test
    fun `a signed figure keeps its sign`() {
        val isolated = BidiText.isolateNumbers("میانگین +0.42 R")
        assertTrue("the sign was orphaned: $isolated", "${BidiText.FSI}+0.42${BidiText.PDI}" in isolated)
    }

    @Test
    fun `a hyphen with no digit after it is not a sign`() {
        assertEquals("اینجا روندی نیست — بازار", BidiText.isolateNumbers("اینجا روندی نیست — بازار"))
    }

    @Test
    fun `it is idempotent`() {
        val once = BidiText.isolateNumbers("در 11% از 9 بار درست بوده.")
        assertEquals(once, BidiText.isolateNumbers(once))
    }

    @Test
    fun `a run already isolated by isolateLtr is left alone`() {
        val already = "قیمت " + BidiText.isolateLtr("91,263.03") + " است."
        assertEquals(already, BidiText.isolateNumbers(already))
    }

    @Test
    fun `Persian digits are numbers too`() {
        val isolated = BidiText.isolateNumbers("از ۴ ابزار روی چارت، ۳ هم‌نظرند.")
        assertEquals(2, marks(isolated))
    }

    @Test
    fun `prose with no figure in it is returned unchanged`() {
        val sentence = "الان هیچ‌کدام از ابزارهای روی این چارت نظری درباره‌ی جهت ندارند."
        assertEquals(sentence, BidiText.isolateNumbers(sentence))
    }

    @Test
    fun `the marks carry no width and strip cleanly`() {
        val isolated = BidiText.isolateNumbers("روی 91,263.03 است.")
        assertEquals(
            "روی 91,263.03 است.",
            isolated.filter { it != BidiText.FSI && it != BidiText.PDI },
        )
    }
}
