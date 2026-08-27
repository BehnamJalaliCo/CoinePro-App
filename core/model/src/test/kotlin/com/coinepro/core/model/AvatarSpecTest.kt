package com.coinepro.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one thing about an avatar that can silently break.
 *
 * Everything else in the feature is a picture on a screen, and a picture that is wrong is obvious.
 * This is the string in the preferences file, and it is read on every launch: a decode that threw
 * would take the profile screen — and the shell's top corner, which draws the same avatar — down
 * with it on a value the reader cannot see or clear without wiping the app's data.
 *
 * So the contract is: every spec survives a round trip, and *nothing* throws.
 */
class AvatarSpecTest {

    @Test
    fun `every base survives a round trip`() {
        val specs = listOf(
            AvatarSpec(AvatarBase.Initial, AvatarRing.GOLD),
            AvatarSpec(AvatarBase.Symbol("BTC"), AvatarRing.ANALYSIS),
            AvatarSpec(AvatarBase.Symbol("XAUUSD"), AvatarRing.PREMIUM),
            AvatarSpec(AvatarBase.Mark(AvatarMark.ROCKET), AvatarRing.BUY),
            AvatarSpec(AvatarBase.Mark(AvatarMark.GLOBE), AvatarRing.NONE),
            AvatarSpec(AvatarBase.Photo("/data/user/0/com.coinepro.app/files/avatar/avatar-1.jpg"), AvatarRing.SELL),
        )
        specs.forEach { spec ->
            assertEquals(spec, AvatarSpec.decode(AvatarSpec.encode(spec)))
        }
    }

    /** A symbol is stored uppercase, so `btc` and `BTC` cannot become two different avatars. */
    @Test
    fun `a symbol is normalised on the way in`() {
        val decoded = AvatarSpec.decode("symbol:btc|GOLD")
        assertEquals(AvatarBase.Symbol("BTC"), decoded.base)
    }

    /**
     * Anything unreadable is the default, and nothing throws.
     *
     * The cases are the real ones: a value from a future release, a half-written file, an empty
     * preference, a mark this build no longer ships. A readable base with an unreadable *ring* is
     * not here — that one keeps its base and is covered below.
     */
    @Test
    fun `an unreadable value decodes to the default rather than throwing`() {
        val broken = listOf(
            null,
            "",
            "   ",
            "garbage",
            "photo:",
            "symbol:",
            "mark:NOT_A_MARK|GOLD",
            "future-kind:something|GOLD",
            "|",
        )
        broken.forEach { value ->
            val decoded = AvatarSpec.decode(value)
            assertEquals("decoding $value", AvatarBase.Initial, decoded.base)
        }
    }

    /**
     * An unknown ring falls back to gold rather than to none.
     *
     * None is a deliberate choice a reader makes; gold is what an unchosen avatar looks like. A
     * corrupted value should land on the default, not on a state that reads as "I turned this off".
     */
    @Test
    fun `an unknown ring falls back to the default ring`() {
        assertEquals(AvatarRing.GOLD, AvatarSpec.decode("mark:ROCKET|NOT_A_RING").ring)
        assertEquals(AvatarRing.NONE, AvatarSpec.decode("mark:ROCKET|NONE").ring)
    }
}
