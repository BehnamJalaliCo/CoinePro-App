package com.coinepro.core.datastore

import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored form of a price alert.
 *
 * One property matters more than the rest and it is the reason this is tested at all: **decoding
 * cannot throw**. This value is read on every launch of the alerts screen, and a parser that
 * crashed on a malformed row would make the screen unreachable without clearing the app's data —
 * losing every other alert to fix one.
 */
class LocalAlertStoreCodecTest {

    private val alert = LocalPriceAlert(
        id = "abc123",
        symbol = "BTCUSDT",
        condition = LocalAlertCondition.PERCENT_UP,
        value = 5.5,
        repeat = AlertRepeat.DAILY,
        referencePrice = 64_182.4,
        active = true,
        createdAtEpochMillis = 1_700_000_000_000L,
        lastFiredAtEpochMillis = 1_700_000_100_000L,
    )

    @Test
    fun `an alert survives a round trip whole`() {
        assertEquals(listOf(alert), LocalAlertStore.decode(LocalAlertStore.encode(listOf(alert))))
    }

    @Test
    fun `the optional fields survive being absent`() {
        val bare = alert.copy(referencePrice = null, lastFiredAtEpochMillis = null, active = false)
        assertEquals(listOf(bare), LocalAlertStore.decode(LocalAlertStore.encode(listOf(bare))))
    }

    @Test
    fun `several alerts keep their order`() {
        val two = listOf(alert, alert.copy(id = "def456", symbol = "ETHUSDT"))
        assertEquals(two, LocalAlertStore.decode(LocalAlertStore.encode(two)))
    }

    /**
     * A bad row is dropped and the good ones are kept.
     *
     * The cases are the real ones: a half-written file, a condition a later release renamed, a
     * number that is not one, and an empty preference.
     */
    @Test
    fun `a malformed row costs only itself`() {
        val good = LocalAlertStore.encode(listOf(alert))
        val broken = listOf(
            "$good;",
            "$good;nonsense",
            "$good;x|BTCUSDT|not_a_condition|1|once|||0|",
            "$good;x|BTCUSDT|above|not_a_number|once|||0|",
            "$good;|||||||| ",
        )
        broken.forEach { raw ->
            val decoded = LocalAlertStore.decode(raw)
            assertEquals("decoding $raw", listOf(alert), decoded)
        }
    }

    @Test
    fun `nothing at all decodes to nothing`() {
        assertTrue(LocalAlertStore.decode(null).isEmpty())
        assertTrue(LocalAlertStore.decode("").isEmpty())
        assertTrue(LocalAlertStore.decode("   ").isEmpty())
    }
}
