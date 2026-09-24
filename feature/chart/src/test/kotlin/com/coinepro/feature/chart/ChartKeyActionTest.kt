package com.coinepro.feature.chart

import androidx.compose.ui.input.key.Key
import com.coinepro.core.chart.ChartType
import com.coinepro.core.marketdata.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard map (5.14.0): one table for the keys and the «?» list, the terminal's bindings, and
 * no chord bound twice — a second binding would never fire and the list would promise it.
 */
class ChartKeyActionTest {

    @Test
    fun `no chord is bound to two actions`() {
        val seen = HashMap<KeyChord, ChartKeyAction>()
        for (action in ChartKeyAction.entries) {
            for (chord in action.chords) {
                val before = seen.put(chord, action)
                assertNull("$chord is bound to both $before and $action", before)
            }
        }
        assertTrue("fewer than the terminal's sixty", seen.size >= 60)
    }

    @Test
    fun `the terminal's bindings reach the right actions`() {
        fun of(key: Key, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) = ChartKeyAction.of(key, ctrl, alt, shift)
        assertEquals(ChartKeyAction.TREND, of(Key.T, alt = true)!!.action)
        assertEquals(ChartKeyAction.FIB, of(Key.F, alt = true)!!.action)
        assertEquals(ChartKeyAction.FULLSCREEN, of(Key.F, ctrl = true, alt = true)!!.action)
        assertEquals(ChartKeyAction.HIDE_ALL, of(Key.H, ctrl = true, alt = true)!!.action)
        assertEquals(ChartKeyAction.HELP, of(Key.Slash, shift = true)!!.action)
        assertEquals(ChartKeyAction.SEARCH, of(Key.Slash)!!.action)
        assertEquals(ChartKeyAction.REPLAY_TEN_BACK, of(Key.DirectionLeft, ctrl = true)!!.action)
        val type = of(Key.Three, alt = true)!!
        assertEquals(ChartType.LINE, ALT_DIGIT_TYPES[type.index])
        val shifted = of(Key.One, shift = true)!!
        assertEquals(Timeframe.M5, SHIFT_DIGIT_TIMEFRAMES[shifted.index])
        val plain = of(Key.Four)!!
        assertEquals(Timeframe.H1, DIGIT_TIMEFRAMES[plain.index])
        // Exactly the modifiers named: Ctrl+Shift+Alt+T is nothing.
        assertNull(of(Key.T, ctrl = true, alt = true, shift = true))
    }

    @Test
    fun `every row of the list names its keys`() {
        for (action in ChartKeyAction.entries) {
            assertTrue("${action.name} has no keys", action.chords.isNotEmpty() && action.combo.isNotBlank())
        }
    }
}
