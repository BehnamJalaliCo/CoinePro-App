package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How an alert is allowed to reach the reader, and how loudly.
 *
 * The case worth spelling out is the difference between *no channels stored* and *no channels
 * chosen*. The first is a row written before this field existed and must come back as the defaults;
 * the second is a reader who deliberately silenced one alert and must come back as silence. A codec
 * that conflates them either unsilences an alert somebody muted or mutes every alert made before
 * this release.
 */
class AlertDeliveryTest {

    @Test
    fun `a new alert arrives and is audible without buzzing`() {
        assertEquals(
            setOf(AlertChannel.PUSH, AlertChannel.IN_APP, AlertChannel.SOUND),
            AlertChannel.DEFAULTS,
        )
        assertFalse(AlertChannel.VIBRATE in AlertChannel.DEFAULTS)
    }

    @Test
    fun `a selection survives a round trip`() {
        val selections = listOf(
            AlertChannel.DEFAULTS,
            setOf(AlertChannel.VIBRATE),
            AlertChannel.entries.toSet(),
        )
        selections.forEach { selection ->
            assertEquals(selection, AlertChannel.decode(AlertChannel.encode(selection)))
        }
    }

    @Test
    fun `an alert silenced on purpose stays silenced, and an absent field takes the defaults`() {
        assertEquals(emptySet<AlertChannel>(), AlertChannel.decode(AlertChannel.encode(emptySet())))
        assertNull(AlertChannel.decode(null))
        assertNull(AlertChannel.decode(""))
    }

    /** A field holding only names from a later release is a row from the future, not a mute. */
    @Test
    fun `a selection of names this version does not know falls back to the defaults`() {
        assertNull(AlertChannel.decode("telegram,carrier_pigeon"))
        assertEquals(setOf(AlertChannel.SOUND), AlertChannel.decode("telegram,sound"))
    }

    @Test
    fun `a sound level is clamped rather than trusted`() {
        assertEquals(AlertSound.MAX_LEVEL, AlertSound.coerce(4f), 0.0001f)
        assertEquals(AlertSound.MIN_LEVEL, AlertSound.coerce(-1f), 0.0001f)
        assertEquals(AlertSound.DEFAULT_LEVEL, AlertSound.coerce(Float.NaN), 0.0001f)
        assertEquals(0.5f, AlertSound.coerce(0.5f), 0.0001f)
    }

    /**
     * The default is below the escalation threshold on purpose.
     *
     * Being louder than the rest of the phone is something a reader opts into for the one alert
     * that deserves it, not something a fresh alert assumes about itself.
     */
    @Test
    fun `only a level above the threshold asks for the alarm output`() {
        assertFalse(AlertSound.isLoud(AlertSound.DEFAULT_LEVEL))
        assertFalse(AlertSound.isLoud(AlertSound.LOUD_THRESHOLD))
        assertTrue(AlertSound.isLoud(AlertSound.MAX_LEVEL))
    }

    @Test
    fun `an alert carries the defaults until the reader changes them`() {
        val alert = LocalPriceAlert(
            id = "a",
            symbol = "BTCUSDT",
            condition = LocalAlertCondition.ABOVE,
            value = 65_000.0,
        )
        assertEquals(AlertChannel.DEFAULTS, alert.channels)
        assertEquals(AlertSound.DEFAULT_LEVEL, alert.effectiveSoundLevel, 0.0001f)
        assertEquals(AlertSound.MAX_LEVEL, alert.copy(soundLevel = 9f).effectiveSoundLevel, 0.0001f)
    }
}
