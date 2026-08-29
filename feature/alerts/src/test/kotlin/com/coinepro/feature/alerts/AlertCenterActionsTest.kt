package com.coinepro.feature.alerts

import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.PriceOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alert centre's interface, pinned.
 *
 * ### What this test is actually protecting
 *
 * Not correctness — nothing here computes anything. It protects **muscle memory**, which is the
 * single most repeated complaint in the negative reviews of this whole category of app: the
 * controls move between releases, and somebody who has pressed the third row a hundred times
 * without reading it deletes an alert. There was a screenshot test in this repository that captured
 * an image of a screen and asserted nothing at all; a picture nobody diffs is not a test.
 *
 * So this asserts the two things a reader's hand actually relies on — **which** actions exist and
 * **in what order** — and it fails on a change to either. If a future release genuinely means to
 * move them, this file is where that decision gets written down and reviewed.
 *
 * Deliberately not a count. A test asserting «five actions» passes while the second and third swap
 * places, which is exactly the change that costs somebody an alert.
 */
class AlertCenterActionsTest {

    private fun row(
        trigger: AlertTrigger? = AlertTrigger.Price(PriceOp.GREATER_THAN, 68_500.0),
        condition: LocalAlertCondition = LocalAlertCondition.ABOVE,
        venue: AlertVenue = AlertVenue.DEVICE,
        active: Boolean = true,
    ) = AlertRow(
        alert = LocalPriceAlert(
            id = "abc123",
            symbol = "BTCUSDT",
            condition = condition,
            value = 68_500.0,
            repeat = AlertRepeat.ONCE,
            active = active,
            trigger = trigger,
        ),
        sentence = "BTC/USDT",
        timeframe = null,
        kind = AlertSectionKind.ARMED,
        venue = venue,
    )

    @Test
    fun `the actions are these, in this order`() {
        assertEquals(
            listOf("history", "pause", "edit", "duplicate", "delete"),
            AlertCenterAction.entries.map(AlertCenterAction::id),
        )
    }

    @Test
    fun `the history is first and the destructive one is last, on every row`() {
        val offered = AlertCenterActions.forRow(row(), canDuplicate = true)

        assertEquals(AlertCenterAction.HISTORY, offered.first())
        assertEquals(AlertCenterAction.DELETE, offered.last())
    }

    @Test
    fun `an ordinary device alert offers all five`() {
        assertEquals(
            AlertCenterAction.entries.toList(),
            AlertCenterActions.forRow(row(), canDuplicate = true),
        )
    }

    @Test
    fun `the header's actions are these, in this order`() {
        // «هشدار تازه» first because it is why anybody opens the screen. A third entry appearing
        // here, or these two swapping, fails this line rather than surprising a thumb.
        assertEquals(listOf("new_alert", "webhooks"), AlertCenterActions.PRIMARY)
    }

    @Test
    fun `a full list drops the copy and leaves everything else where it was`() {
        val offered = AlertCenterActions.forRow(row(), canDuplicate = false)

        assertFalse(AlertCenterAction.DUPLICATE in offered)
        assertEquals(
            listOf(
                AlertCenterAction.HISTORY,
                AlertCenterAction.PAUSE,
                AlertCenterAction.EDIT,
                AlertCenterAction.DELETE,
            ),
            offered,
        )
    }

    @Test
    fun `a server alert can be paused and deleted but not edited or copied`() {
        // The server's route has a create, a pause and a delete, and no update. An edit built out
        // of a delete and a create would leave the reader with neither if the second call failed.
        val offered = AlertCenterActions.forRow(row(venue = AlertVenue.SERVER), canDuplicate = true)

        assertEquals(
            listOf(AlertCenterAction.HISTORY, AlertCenterAction.PAUSE, AlertCenterAction.DELETE),
            offered,
        )
    }

    @Test
    fun `an alert the editor cannot express is not offered an editor`() {
        // A 24-hour-change condition reads the feed's own daily percentage, which no trigger
        // measures. Opening a sheet on it would quietly rewrite what the alert means.
        val offered = AlertCenterActions.forRow(
            row(trigger = null, condition = LocalAlertCondition.CHANGE_24H_OVER),
            canDuplicate = true,
        )

        assertFalse(AlertCenterAction.EDIT in offered)
        assertTrue(AlertCenterAction.HISTORY in offered)
    }

    @Test
    fun `a drawing alert is editable now that the sheet can make one`() {
        val offered = AlertCenterActions.forRow(
            row(trigger = AlertTrigger.DrawingTouch("77")),
            canDuplicate = true,
        )

        assertTrue(AlertCenterAction.EDIT in offered)
    }
}
