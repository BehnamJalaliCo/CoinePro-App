package com.coinepro.core.datastore

import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertSound
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.AuditEvent
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.PriceOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored form of the audit log.
 *
 * Two properties are asserted here and both are the reason the log is worth having. It **keeps the
 * newest entries**, because the question is always about the last few hours; and it **cannot throw
 * while decoding**, because a history screen that crashes on its own stored value is a history
 * screen that is unreachable exactly when somebody is trying to find out why they were not told.
 */
class AlertAuditStoreTest {

    private val fired = AlertAuditEntry(
        alertId = "abc123",
        event = AuditEvent.FIRED,
        at = 1_700_000_000_000L,
        symbol = "BTCUSDT",
        price = 64_182.4,
        timeframe = "1h",
        note = "crossed the line",
    )

    @Test
    fun `an entry survives a round trip whole`() {
        assertEquals(listOf(fired), AlertAuditStore.decode(AlertAuditStore.encode(listOf(fired))))
    }

    @Test
    fun `the optional fields survive being absent`() {
        val bare = fired.copy(
            event = AuditEvent.DELETED,
            price = null,
            timeframe = null,
            note = null,
        )
        assertEquals(listOf(bare), AlertAuditStore.decode(AlertAuditStore.encode(listOf(bare))))
    }

    /**
     * A delivery failure's own message is the most valuable line in the log and the least
     * predictable text in the app, so it is the one that has to survive the separators intact.
     */
    @Test
    fun `a note containing the format's own separators survives`() {
        val awkward = fired.copy(
            event = AuditEvent.DELIVERY_FAILED,
            note = "failed; retried | gave up \\ at last",
        )
        val decoded = AlertAuditStore.decode(AlertAuditStore.encode(listOf(awkward)))
        assertEquals(listOf(awkward), decoded)
    }

    @Test
    fun `every event survives a round trip through its id`() {
        AuditEvent.entries.forEach { event ->
            val entry = fired.copy(event = event)
            assertEquals(listOf(entry), AlertAuditStore.decode(AlertAuditStore.encode(listOf(entry))))
        }
    }

    @Test
    fun `the newest entry goes to the front`() {
        val older = fired.copy(at = 1L)
        val newer = fired.copy(at = 2L)
        assertEquals(listOf(newer, older), AlertAuditStore.prepend(listOf(older), newer))
    }

    /**
     * The trim is at five hundred, oldest out, and it happens on the write rather than on the read.
     *
     * Five hundred and one entries in the file would be five hundred and one parsed on every open,
     * which is the cost this cap exists to bound.
     */
    @Test
    fun `the log stops at five hundred and drops the oldest`() {
        assertEquals(500, AlertAuditStore.MAX_ENTRIES)
        // Newest first, as the log is kept: five hundred down to one.
        val full = (AlertAuditStore.MAX_ENTRIES downTo 1).map { at -> fired.copy(at = at.toLong()) }
        val trimmed = AlertAuditStore.prepend(full, fired.copy(at = 9_999L))
        assertEquals(AlertAuditStore.MAX_ENTRIES, trimmed.size)
        assertEquals(9_999L, trimmed.first().at)
        assertEquals(2L, trimmed.last().at)
        assertTrue(trimmed.none { it.at == 1L })
    }

    @Test
    fun `a stored log longer than the cap is trimmed as it is read`() {
        val tooMany = (600 downTo 1).map { at -> fired.copy(at = at.toLong()) }
        val decoded = AlertAuditStore.decode(AlertAuditStore.encode(tooMany))
        assertEquals(AlertAuditStore.MAX_ENTRIES, decoded.size)
        assertEquals(600L, decoded.first().at)
        assertEquals(101L, decoded.last().at)
    }

    /**
     * A bad line costs only itself.
     *
     * The cases are the real ones: a half-written file, an event a later release added, a timestamp
     * that is not a number, and an empty preference.
     */
    @Test
    fun `a malformed row costs only itself`() {
        val good = AlertAuditStore.encode(listOf(fired))
        listOf(
            "$good;",
            "$good;nonsense",
            "$good;abc|invented_event|1|BTCUSDT|||",
            "$good;abc|fired|not_a_number|BTCUSDT|||",
            "$good;|fired|1|BTCUSDT|||",
            "$good;||||||",
        ).forEach { raw ->
            assertEquals("decoding $raw", listOf(fired), AlertAuditStore.decode(raw))
        }
    }

    @Test
    fun `nothing at all decodes to nothing`() {
        assertTrue(AlertAuditStore.decode(null).isEmpty())
        assertTrue(AlertAuditStore.decode("").isEmpty())
        assertTrue(AlertAuditStore.decode("   ").isEmpty())
    }
}

/**
 * An alert row written before triggers, scopes, expiry, channels and messages existed.
 *
 * This is the compatibility guarantee the alerts format makes, and it is worth a test of its own
 * because it is the guarantee that is easiest to break by accident: every field added since the
 * format shipped is **appended**, so a nine-field row from an older release still parses
 * field-for-field and simply stops early, taking the default for everything after it. Getting this
 * wrong does not throw in a way anybody notices during development — it drops the reader's alerts.
 *
 * It lives beside the audit store's test because both are the same promise about the same
 * preferences file.
 */
class LocalAlertStoreLegacyRowTest {

    /** Exactly what the first version of this format wrote: nine fields and no more. */
    private val legacyRow = "abc123|BTCUSDT|percent_up|5.5|daily|64182.4|1|1700000000000|1700000100000"

    @Test
    fun `a nine-field row from an older release decodes without throwing`() {
        val decoded = LocalAlertStore.decode(legacyRow)
        assertEquals(1, decoded.size)
        val alert = decoded.single()
        assertEquals("abc123", alert.id)
        assertEquals("BTCUSDT", alert.symbol)
        assertEquals(LocalAlertCondition.PERCENT_UP, alert.condition)
        assertEquals(5.5, alert.value, 0.0001)
        assertEquals(AlertRepeat.DAILY, alert.repeat)
        assertEquals(64_182.4, alert.referencePrice!!, 0.0001)
        assertTrue(alert.active)
        assertEquals(1_700_000_000_000L, alert.createdAtEpochMillis)
        assertEquals(1_700_000_100_000L, alert.lastFiredAtEpochMillis)
    }

    /** Everything added later takes its default, which in every case means "as it was before". */
    @Test
    fun `an older row takes the defaults for everything added since`() {
        val alert = LocalAlertStore.decode(legacyRow).single()
        assertNull(alert.trigger)
        assertNull(alert.scope)
        assertNull(alert.frequency)
        assertNull(alert.expiresAt)
        assertNull(alert.message)
        assertEquals(AlertScope.Symbol("BTCUSDT"), alert.effectiveScope)
        assertEquals(AlertChannel.DEFAULTS, alert.channels)
        assertEquals(AlertSound.DEFAULT_LEVEL, alert.soundLevel, 0.0001f)
    }

    @Test
    fun `a row missing even the original fields is dropped rather than half-built`() {
        assertTrue(LocalAlertStore.decode("abc123|BTCUSDT|above").isEmpty())
    }

    @Test
    fun `an alert carrying all of the new fields survives a round trip`() {
        val alert = LocalPriceAlert(
            id = "def456",
            symbol = "ETHUSDT",
            condition = LocalAlertCondition.ABOVE,
            value = 2_500.0,
            repeat = AlertRepeat.ALWAYS,
            referencePrice = null,
            active = true,
            createdAtEpochMillis = 1_700_000_000_000L,
            lastFiredAtEpochMillis = null,
            trigger = AlertTrigger.MultiCondition(
                listOf(
                    AlertTrigger.Price(PriceOp.CROSSING_UP, 2_500.0),
                    AlertTrigger.Channel(ChannelOp.INSIDE, 2_400.0, 2_600.0),
                ),
            ),
            scope = AlertScope.Watchlist("main"),
            frequency = AlertFrequency.ONCE_PER_BAR_CLOSE,
            expiresAt = 1_800_000_000_000L,
            channels = setOf(AlertChannel.PUSH, AlertChannel.VIBRATE),
            soundLevel = 0.95f,
            message = "{symbol} hit {price}; sell half | keep the rest",
        )
        assertEquals(listOf(alert), LocalAlertStore.decode(LocalAlertStore.encode(listOf(alert))))
    }

    /** A silenced alert must not come back with the defaults; that would unsilence it. */
    @Test
    fun `an alert with every channel turned off stays silent through a round trip`() {
        val silent = LocalPriceAlert(
            id = "ghi789",
            symbol = "BTCUSDT",
            condition = LocalAlertCondition.BELOW,
            value = 60_000.0,
            channels = emptySet(),
        )
        assertEquals(emptySet<AlertChannel>(), LocalAlertStore.decode(LocalAlertStore.encode(listOf(silent))).single().channels)
    }
}
