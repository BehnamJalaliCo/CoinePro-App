package com.coinepro.feature.script

import com.coinepro.core.script.ScriptStrategies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Every shipped strategy has a name and a description, in both languages.
 *
 * The two halves of a strategy live in different modules — the script in `core:script`, the prose
 * in this module's twinned `strings.xml` pair — so nothing but a test holds them together. Without
 * this, adding a strategy and forgetting its strings ships a library row with a blank title, and
 * the blank row is only discovered by somebody scrolling the finished screen.
 */
class StrategyTextTest {

    @Test
    fun `every strategy has a name and a description`() {
        for (strategy in ScriptStrategies.ALL) {
            assertNotEquals("«${strategy.id}» نامی ندارد", 0, nameOf(strategy))
            assertNotEquals("«${strategy.id}» توضیحی ندارد", 0, descriptionOf(strategy))
        }
    }

    @Test
    fun `no two strategies share a name or a description`() {
        // A copied line in the resource file is the easy mistake here, and it reads as two
        // different studies claiming to do the same thing.
        val names = ScriptStrategies.ALL.map { nameOf(it) }
        val descriptions = ScriptStrategies.ALL.map { descriptionOf(it) }
        assertEquals(names.size, names.toSet().size)
        assertEquals(descriptions.size, descriptions.toSet().size)
    }
}
