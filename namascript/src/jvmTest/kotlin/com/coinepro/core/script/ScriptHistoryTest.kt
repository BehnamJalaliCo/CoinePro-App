package com.coinepro.core.script

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a saved script's history can and cannot be trusted to round-trip (4.82.0).
 *
 * `ScriptController` stores five earlier revisions in one column. The first encoding for that used
 * two control characters — U+001E and U+001F — on the reasoning that NamaScript cannot contain
 * them. **This file is the test that proved that wrong**, and the reason the encoding is now
 * length-prefixed instead: the lexer refuses a control character in the code, and accepts one
 * inside a string literal, which is exactly where a reader's own label lives.
 *
 * The test stays after the fix rather than being deleted with the assumption it disproved. It is
 * the record of why the column is shaped the way it is, and it fails again the day somebody
 * reaches for a separator.
 */
class ScriptHistoryTest {

    private val separators = listOf(RECORD, FIELD)

    @Test
    fun `neither separator can occur in a script that compiles`() {
        for (character in separators) {
            val source = "x = ta.ema(close, 20)$character\nplot(x)"
            assertNotNull(
                "U+${character.code.toString(16).uppercase()} was accepted in the code",
                NamaScript.check(source),
            )
        }
    }

    @Test
    fun `but both can hide inside a string literal, which is why the history is length-prefixed`() {
        // The finding. A string literal is not scanned for anything but its closing quote, so a
        // reader's own label can carry a separator — and a delimiter-based history would have split
        // a record in half there, losing every earlier version after it.
        for (character in separators) {
            val source = "plot(close, title = \"a${character}b\")"
            assertNull(
                "U+${character.code.toString(16).uppercase()} is refused inside a string after all — " +
                    "if that is now true, the history could go back to a separator",
                NamaScript.check(source),
            )
        }
    }

    @Test
    fun `everything else a reader writes passes through`() {
        // Everything ordinary goes through the same column, and none of it is what broke the old
        // encoding — quotes, brackets, Persian, guillemets and per-cent signs were never the risk.
        val awkward = """
            note = "«۳ کندل»، a and b, 100%"
            average = ta.ema(close, 20)
            plot(average, title = note)
        """.trimIndent()
        assertNull("the awkward script does not compile", NamaScript.check(awkward))
        for (character in separators) {
            assertTrue("the fixture already contains a separator", character !in awkward)
        }
    }

    private companion object {
        /** U+001E, the record separator: one revision from the next. */
        const val RECORD = ''

        /** U+001F, the unit separator: a revision's timestamp from its source. */
        const val FIELD = ''
    }
}
