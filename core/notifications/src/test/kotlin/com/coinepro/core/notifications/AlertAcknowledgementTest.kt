package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **«تا وقتی ببینمش»** — the repeat that ends on an answer rather than on a clock (run Τ2, B9).
 *
 * The three policies that existed cannot say it. [AlertRepeat.ONCE] tells the reader once, which is
 * nothing if the phone was face down; [AlertRepeat.ALWAYS] re-fires only while the condition keeps
 * *becoming* true again, so a price that crosses a line and stays there is announced once and never
 * again; [AlertRepeat.DAILY] is a day late. This one keeps speaking until somebody answers, and an
 * answer is the only thing that stops it — not a count, not a timeout, because an alert that gives
 * up on its own is the missed alert it was meant to prevent.
 */
class AlertAcknowledgementTest {

    private fun alert(
        repeat: AlertRepeat = AlertRepeat.UNTIL_ACKNOWLEDGED,
        everyMinutes: Int? = null,
        lastFired: Long? = null,
        acknowledged: Long? = null,
    ) = LocalPriceAlert(
        id = "a1",
        symbol = "BTCUSDT",
        condition = LocalAlertCondition.ABOVE,
        value = 100.0,
        repeat = repeat,
        repeatEveryMinutes = everyMinutes,
        lastFiredAtEpochMillis = lastFired,
        acknowledgedAtEpochMillis = acknowledged,
    )

    private fun due(alert: LocalPriceAlert, now: Long, price: Double = 101.0): Boolean =
        LocalPriceAlert.due(
            alert = alert,
            previous = null,
            price = price,
            series = null,
            changePercent24h = null,
            nowEpochMillis = now,
            barStart = 0L,
            barClosed = false,
        )

    @Test
    fun `it speaks again after the interval, and not before`() {
        val fired = alert(everyMinutes = 5, lastFired = 0L)
        assertFalse("it repeated after four minutes of a five-minute interval", due(fired, MINUTE * 4))
        assertTrue("it did not repeat after five", due(fired, MINUTE * 5))
    }

    @Test
    fun `an acknowledgement ends it, whatever the clock says`() {
        val seen = alert(everyMinutes = 5, lastFired = 0L).acknowledged(MINUTE)
        assertEquals(MINUTE, seen.acknowledgedAtEpochMillis)
        // Deactivated as well as stamped: «until I acknowledge it» is a statement about one event,
        // and leaving it armed would make the answer mean «be quiet for now», which is a different
        // promise from the one the editor offers.
        assertFalse("an acknowledged alert is still armed", seen.active)
        assertFalse("it repeated an hour after being acknowledged", due(seen, MINUTE * 60))
    }

    @Test
    fun `acknowledging anything else is a no-op, so a daily alert cannot be lost by it`() {
        // The receiver and the activity both acknowledge by id without looking at the policy, which
        // is only safe because this is where the rule lives.
        AlertRepeat.entries.filter { it != AlertRepeat.UNTIL_ACKNOWLEDGED }.forEach { policy ->
            val other = alert(repeat = policy, lastFired = 0L)
            val after = other.acknowledged(MINUTE)
            assertEquals("acknowledging a $policy alert changed it", other, after)
            assertNull(after.acknowledgedAtEpochMillis)
            assertTrue("acknowledging a $policy alert switched it off", after.active)
        }
    }

    @Test
    fun `a nonsense interval becomes the default rather than a stream`() {
        // Nothing in this app writes these. A preferences file that has been edited, restored from
        // another device or truncated can hold them, and the failure mode is a notification on
        // every evaluation pass — which is the one bug in an alerts feature nobody forgives.
        assertEquals(
            LocalPriceAlert.DEFAULT_REPEAT_MINUTES * MINUTE,
            alert(everyMinutes = null).effectiveRepeatMillis,
        )
        assertEquals(LocalPriceAlert.MIN_REPEAT_MINUTES * MINUTE, alert(everyMinutes = 0).effectiveRepeatMillis)
        assertEquals(LocalPriceAlert.MIN_REPEAT_MINUTES * MINUTE, alert(everyMinutes = -30).effectiveRepeatMillis)
        assertEquals(LocalPriceAlert.MAX_REPEAT_MINUTES * MINUTE, alert(everyMinutes = 99_999).effectiveRepeatMillis)
    }

    @Test
    fun `firing keeps it armed, because the whole point is that it comes back`() {
        val after = alert(everyMinutes = 5).fired(MINUTE)
        assertTrue("it switched itself off after speaking once", after.active)
        assertEquals(MINUTE, after.lastFiredAtEpochMillis)
    }

    @Test
    fun `the notification carries the button from the first firing, and the screen does not`() {
        // Two different questions, and they were one property until this test. The notification is
        // built *before* the fire stamp is written, so a button conditioned on «has fired» would
        // never appear; a list row that marked an alert as waiting before it had ever spoken would
        // be telling the reader they had missed something that has not happened.
        val armed = alert(everyMinutes = 5)
        assertTrue("a fresh alert would get no acknowledge button", armed.repeatsUntilAcknowledged)
        assertFalse("a fresh alert is already marked as waiting", armed.awaitsAcknowledgement)

        val spoken = armed.fired(MINUTE)
        assertTrue(spoken.repeatsUntilAcknowledged)
        assertTrue(spoken.awaitsAcknowledgement)

        val seen = spoken.acknowledged(MINUTE * 2)
        assertFalse(seen.repeatsUntilAcknowledged)
        assertFalse(seen.awaitsAcknowledgement)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
