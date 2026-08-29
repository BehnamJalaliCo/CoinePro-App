package com.coinepro.feature.alerts

import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the list is cut into waiting, just-fired and finished.
 *
 * Every boundary here is a comparison against a clock the caller supplies, which is what makes the
 * awkward cases — a one-shot that fired ten minutes ago, an alert whose own expiry has passed, a
 * device whose clock moved backwards — assertions rather than an afternoon of waiting.
 */
class AlertGroupingTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `an alert that has never fired is waiting`() {
        assertEquals(AlertSectionKind.ARMED, AlertGrouping.kindOf(alert(), now))
    }

    @Test
    fun `an alert the reader paused is still waiting rather than expired`() {
        val paused = alert(active = false)

        assertEquals(AlertSectionKind.ARMED, AlertGrouping.kindOf(paused, now))
    }

    @Test
    fun `an alert that fired an hour ago is in the recent section`() {
        val fired = alert(lastFired = now - hour)

        assertEquals(AlertSectionKind.FIRED, AlertGrouping.kindOf(fired, now))
    }

    @Test
    fun `the recent window is a whole day and its edge still counts as recent`() {
        // A one-shot, so that leaving the window has somewhere to go: a repeating alert past the
        // window is waiting again, which the case below this one asserts.
        val atTheEdge = alert(frequency = AlertFrequency.ONCE, lastFired = now - day)
        val justPastIt = alert(frequency = AlertFrequency.ONCE, lastFired = now - day - 1)

        assertEquals(AlertSectionKind.FIRED, AlertGrouping.kindOf(atTheEdge, now))
        assertEquals(AlertSectionKind.EXPIRED, AlertGrouping.kindOf(justPastIt, now))
    }

    @Test
    fun `a repeating alert that leaves the recent window is waiting rather than finished`() {
        val repeating = alert(frequency = AlertFrequency.EVERY_TIME, lastFired = now - day - 1)

        assertEquals(AlertSectionKind.ARMED, AlertGrouping.kindOf(repeating, now))
    }

    @Test
    fun `a one-shot that has just fired is shown as fired rather than as finished`() {
        val spent = alert(frequency = AlertFrequency.ONCE, active = false, lastFired = now - 10 * minute)

        assertEquals(AlertSectionKind.FIRED, AlertGrouping.kindOf(spent, now))
    }

    @Test
    fun `a one-shot that fired last week is finished`() {
        val spent = alert(frequency = AlertFrequency.ONCE, active = false, lastFired = now - 7 * day)

        assertEquals(AlertSectionKind.EXPIRED, AlertGrouping.kindOf(spent, now))
    }

    @Test
    fun `a repeating alert that fired last week is waiting again`() {
        val repeating = alert(frequency = AlertFrequency.EVERY_TIME, lastFired = now - 7 * day)

        assertEquals(AlertSectionKind.ARMED, AlertGrouping.kindOf(repeating, now))
    }

    @Test
    fun `an alert with no frequency falls back to its older repeat policy`() {
        val oneShot = alert(repeat = AlertRepeat.ONCE, lastFired = now - 7 * day)
        val always = alert(repeat = AlertRepeat.ALWAYS, lastFired = now - 7 * day)

        assertEquals(AlertSectionKind.EXPIRED, AlertGrouping.kindOf(oneShot, now))
        assertEquals(AlertSectionKind.ARMED, AlertGrouping.kindOf(always, now))
    }

    @Test
    fun `the readers own expiry beats everything else including a firing a minute ago`() {
        val expired = alert(lastFired = now - minute, expiresAt = now - hour)

        assertEquals(AlertSectionKind.EXPIRED, AlertGrouping.kindOf(expired, now))
    }

    @Test
    fun `a firing stamped in the future is treated as having just happened`() {
        // A device clock that moved backwards, which is a real state and not a corrupt row. The
        // alternative — treating it as never fired — hides the firing the reader is looking for.
        val skewed = alert(lastFired = now + hour)

        assertEquals(AlertSectionKind.FIRED, AlertGrouping.kindOf(skewed, now))
    }

    @Test
    fun `the sections come back in a fixed order and empty ones are dropped`() {
        val sections = AlertGrouping.group(
            listOf(
                alert(id = "waiting"),
                alert(id = "fired", lastFired = now - hour),
            ),
            now,
        )

        assertEquals(listOf(AlertSectionKind.ARMED, AlertSectionKind.FIRED), sections.map { it.kind })
        assertEquals(listOf("waiting"), sections[0].alerts.map { it.id })
        assertEquals(listOf("fired"), sections[1].alerts.map { it.id })
    }

    @Test
    fun `paused alerts sink under the live ones inside the waiting section`() {
        val sections = AlertGrouping.group(
            listOf(
                alert(id = "paused-new", active = false, createdAt = now),
                alert(id = "live-old", createdAt = now - day),
                alert(id = "live-new", createdAt = now - minute),
            ),
            now,
        )

        assertEquals(
            listOf("live-new", "live-old", "paused-new"),
            sections.single().alerts.map { it.id },
        )
    }

    @Test
    fun `the fired section is ordered by when each one went off`() {
        val sections = AlertGrouping.group(
            listOf(
                alert(id = "older", lastFired = now - 5 * hour),
                alert(id = "newer", lastFired = now - hour),
            ),
            now,
        )

        assertEquals(listOf("newer", "older"), sections.single().alerts.map { it.id })
    }

    private fun alert(
        id: String = "alert",
        active: Boolean = true,
        createdAt: Long = 0L,
        lastFired: Long? = null,
        expiresAt: Long? = null,
        repeat: AlertRepeat = AlertRepeat.ALWAYS,
        frequency: AlertFrequency? = null,
    ) = LocalPriceAlert(
        id = id,
        symbol = "BTCUSDT",
        condition = LocalAlertCondition.ABOVE,
        value = 1.0,
        repeat = repeat,
        active = active,
        createdAtEpochMillis = createdAt,
        lastFiredAtEpochMillis = lastFired,
        frequency = frequency,
        expiresAt = expiresAt,
    )
}
