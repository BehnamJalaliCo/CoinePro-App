package com.coinepro.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the teaching catalogue's *keys*, which are the part of it that lives on somebody's phone.
 *
 * The copy itself is checked by the compiler — a missing string is a build failure — and by reading
 * it. What no compiler can catch is a key: it crosses a module boundary as a bare string, it is
 * written to disk, and getting one wrong is silent in both directions. A duplicate key makes two
 * screens share one dismissal, so closing the banner on Markets closes it on the chart as well. A
 * key with a character `TeachingStore.usable` rejects is never stored at all, so that screen's
 * banner comes back forever and no test in `core:datastore` can see it, because that module has
 * never heard of this enum.
 */
class CoineProTeachingTest {

    @Before
    fun clearSessionDismissals() {
        SessionTeachingDismissals.forgetAll()
    }

    @Test
    fun `every surface has its own key`() {
        val keys = TeachingSurface.entries.map { it.key }

        // Sharing one would make dismissing one screen's banner dismiss another's.
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `every key is a shape the dismissal store will accept`() {
        // `TeachingStore.usable`'s rule, restated here because neither module can see the other:
        // lowercase letters, digits, underscore, hyphen and the dot that carries a revision. A key
        // outside it is dropped on the way to disk and that screen's banner never stays closed.
        TeachingSurface.entries.forEach { surface ->
            assertTrue(surface.key, surface.key.isNotEmpty())
            assertTrue(surface.key, surface.key.length <= MAX_KEY_LENGTH)
            assertTrue(
                surface.key,
                surface.key.all { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' || it == '.' },
            )
        }
    }

    @Test
    fun `the key is not derived from the enum name, so renaming the constant cannot un-dismiss a banner`() {
        // Kept deliberately: `PAPER_TRADE.key` is `paper_trade` because it was written down, not
        // because of a lowercase-and-underscore rule somebody could later "simplify".
        assertEquals("paper_trade", TeachingSurface.PAPER_TRADE.key)
        assertEquals("dom", TeachingSurface.DOM.key)
    }

    @Test
    fun `no two surfaces share a sentence`() {
        // Copy-pasting a row and forgetting to change the resource is the easy mistake here, and it
        // produces a screen that confidently explains a different screen.
        val leads = TeachingSurface.entries.map { it.lead }
        val pitfalls = TeachingSurface.entries.mapNotNull { it.pitfall }

        assertEquals(leads.size, leads.toSet().size)
        assertEquals(pitfalls.size, pitfalls.toSet().size)
        assertTrue(leads.none { it in pitfalls })
    }

    @Test
    fun `every surface shipped today also names the thing readers get wrong`() {
        // Nullable in the type because a future screen might genuinely have no common misreading.
        // None of the ones here does: the second line is the half that earns the banner its space.
        val silent = TeachingSurface.entries.filter { it.pitfall == null }

        assertEquals(emptyList<TeachingSurface>(), silent)
    }

    @Test
    fun `the whole app is covered`() {
        // Not a count for its own sake: a screen added without a surface is a screen that teaches
        // nothing, and the omission is invisible until somebody opens it and cannot tell what it is.
        assertEquals(23, TeachingSurface.entries.size)
    }

    @Test
    fun `dismissing without a host lasts the process rather than throwing`() {
        SessionTeachingDismissals.dismiss(TeachingSurface.MARKETS.key)

        assertTrue(TeachingSurface.MARKETS.key in SessionTeachingDismissals.dismissed)
    }

    @Test
    fun `restoring puts one banner back and leaves the others away`() {
        SessionTeachingDismissals.dismiss(TeachingSurface.MARKETS.key)
        SessionTeachingDismissals.dismiss(TeachingSurface.DOM.key)

        SessionTeachingDismissals.restore(TeachingSurface.DOM.key)

        assertTrue(TeachingSurface.MARKETS.key in SessionTeachingDismissals.dismissed)
        assertFalse(TeachingSurface.DOM.key in SessionTeachingDismissals.dismissed)
    }

    @Test
    fun `an unhosted implementation is ready immediately, so nothing waits on a disk read`() {
        assertTrue(SessionTeachingDismissals.ready)
    }
}

/** `TeachingStore.MAX_KEY_LENGTH`, which this module cannot import. See the class note. */
private const val MAX_KEY_LENGTH = 48
