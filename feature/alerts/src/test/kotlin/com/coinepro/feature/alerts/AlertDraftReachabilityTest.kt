package com.coinepro.feature.alerts

import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertSound
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.PriceAlertCondition
import com.coinepro.core.notifications.PriceAlertTrigger
import com.coinepro.core.notifications.PriceOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three things this editor could not previously make.
 *
 * `AlertTrigger.DrawingTouch`, `AlertScope.Watchlist` and `LocalPriceAlert.soundLevel` were all
 * built, tested and read by the evaluator and the deliverer — and no path through the app produced
 * any of them. A drawing alert had no gesture, `toAlert` wrote `AlertScope.Symbol` unconditionally,
 * and the sound level had no control, so `AlertSound.isLoud` was never true and a whole Android
 * notification channel was dead code.
 *
 * These tests assert the *output of the draft*, which is the seam where that failure lived: every
 * one of them would have passed against a working evaluator and a screen nobody could reach.
 */
class AlertDraftReachabilityTest {

    private val delta = 1e-9

    // ── drawings ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a drawing condition builds a DrawingTouch on the chosen drawing`() {
        val draft = AlertDraft(
            symbol = "BTCUSDT",
            conditions = listOf(
                AlertConditionDraft(kind = AlertTriggerKind.DRAWING, drawingId = "1731059442"),
            ),
        )

        val trigger = draft.trigger()

        assertEquals(AlertTrigger.DrawingTouch("1731059442"), trigger)
        assertTrue("the sheet's save must be live for a drawing alert", draft.valid)
    }

    @Test
    fun `a drawing condition with nothing picked builds nothing, and never throws`() {
        // `AlertTrigger.DrawingTouch` requires a non-blank id. The sheet asks for a trigger on every
        // keystroke to decide whether save is live, so a draft that let that `require` run would
        // take the sheet down while the reader was still choosing.
        val empty = AlertConditionDraft(kind = AlertTriggerKind.DRAWING)

        assertNull(empty.build())
        assertFalse(AlertDraft(symbol = "BTCUSDT", conditions = listOf(empty)).valid)
    }

    @Test
    fun `a drawing alert reopens in the editor on the drawing it was made with`() {
        val row = AlertConditionDraft.of(AlertTrigger.DrawingTouch("77"))

        assertNotNull(row)
        assertEquals(AlertTriggerKind.DRAWING, row?.kind)
        assertEquals("77", row?.drawingId)
    }

    @Test
    fun `a drawing condition sits beside another condition in one alert`() {
        // The reason `MultiCondition` is asked one condition at a time by `AlertConditions`: a
        // price condition and a drawing touch read different numbers on the same sample.
        val draft = AlertDraft(
            symbol = "BTCUSDT",
            conditions = listOf(
                AlertConditionDraft(kind = AlertTriggerKind.PRICE, first = "68500"),
                AlertConditionDraft(kind = AlertTriggerKind.DRAWING, drawingId = "9"),
            ),
        )

        val trigger = draft.trigger() as? AlertTrigger.MultiCondition

        assertNotNull(trigger)
        assertEquals(
            listOf(AlertTrigger.Price(PriceOp.CROSSING_UP, 68_500.0), AlertTrigger.DrawingTouch("9")),
            trigger?.conditions,
        )
    }

    // ── watchlist scope ─────────────────────────────────────────────────────────────────────

    @Test
    fun `choosing a list writes a watchlist scope rather than the symbol`() {
        val draft = AlertDraft(
            symbol = "BTCUSDT",
            scopeListId = "movers",
            conditions = listOf(AlertConditionDraft(first = "68500")),
        )

        val alert = draft.toAlert(existing = null, id = "abc", nowEpochMillis = 1_000L)

        assertEquals(AlertScope.Watchlist("movers"), alert?.scope)
    }

    @Test
    fun `a watchlist alert resolves to the list's members, now rather than when it was made`() {
        val draft = AlertDraft(
            symbol = "BTCUSDT",
            scopeListId = "movers",
            conditions = listOf(AlertConditionDraft(first = "68500")),
        )
        val alert = requireNotNull(draft.toAlert(existing = null, id = "abc", nowEpochMillis = 0L))

        // The membership function is asked at evaluation time, which is the whole promise of the
        // scope: a symbol starred afterwards is covered without the alert being re-made.
        val before = alert.symbols { listOf("BTCUSDT", "ETHUSDT") }
        val after = alert.symbols { listOf("BTCUSDT", "ETHUSDT", "SOLUSDT") }

        assertEquals(listOf("BTCUSDT", "ETHUSDT"), before)
        assertEquals(listOf("BTCUSDT", "ETHUSDT", "SOLUSDT"), after)
    }

    @Test
    fun `no list chosen still writes the single symbol, upper-cased`() {
        val draft = AlertDraft(symbol = "btcusdt", conditions = listOf(AlertConditionDraft(first = "1")))

        val alert = draft.toAlert(existing = null, id = "abc", nowEpochMillis = 0L)

        assertEquals(AlertScope.Symbol("BTCUSDT"), alert?.scope)
        assertEquals("BTCUSDT", alert?.symbol)
    }

    @Test
    fun `a watchlist alert reopens with its list still chosen`() {
        val draft = AlertDraft(
            symbol = "BTCUSDT",
            scopeListId = "movers",
            conditions = listOf(AlertConditionDraft(first = "68500")),
        )
        val alert = requireNotNull(draft.toAlert(existing = null, id = "abc", nowEpochMillis = 0L))

        assertEquals("movers", AlertDraft.of(alert)?.scopeListId)
    }

    // ── loudness ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the loud step is loud and the other two are not`() {
        // The one assertion that would have caught a dead notification channel: `isLoud` decides
        // which Android channel the notification is posted to, and nothing could make it true.
        assertTrue(AlertLoudness.LOUD.isLoud)
        assertFalse(AlertLoudness.NORMAL.isLoud)
        assertFalse(AlertLoudness.QUIET.isLoud)
        assertTrue("the loud step must clear the threshold, not sit on it", AlertLoudness.LOUD.level > AlertSound.LOUD_THRESHOLD)
    }

    @Test
    fun `the chosen loudness reaches the stored alert`() {
        val draft = AlertDraft(
            symbol = "BTCUSDT",
            conditions = listOf(AlertConditionDraft(first = "68500")),
            soundLevel = AlertLoudness.LOUD.level,
        )

        val alert = requireNotNull(draft.toAlert(existing = null, id = "abc", nowEpochMillis = 0L))

        assertEquals(AlertLoudness.LOUD.level, alert.soundLevel, delta.toFloat())
        assertTrue(AlertSound.isLoud(alert.effectiveSoundLevel))
    }

    @Test
    fun `editing an alert keeps the loudness it was made with`() {
        val loud = requireNotNull(
            AlertDraft(
                symbol = "BTCUSDT",
                conditions = listOf(AlertConditionDraft(first = "68500")),
                soundLevel = AlertLoudness.LOUD.level,
            ).toAlert(existing = null, id = "abc", nowEpochMillis = 0L),
        )

        val reopened = requireNotNull(AlertDraft.of(loud))

        assertEquals(AlertLoudness.LOUD, AlertLoudness.of(reopened.soundLevel))
        assertTrue(reopened.loud)
    }

    @Test
    fun `a level from outside the three steps still shows a position`() {
        // A hand-edited preference, or a level from a build with different steps. Nearest rather
        // than exact, so the control is never drawn with nothing selected.
        assertEquals(AlertLoudness.NORMAL, AlertLoudness.of(0.68f))
        assertEquals(AlertLoudness.QUIET, AlertLoudness.of(0.0f))
        assertEquals(AlertLoudness.LOUD, AlertLoudness.of(1.4f))
        assertEquals(AlertLoudness.NORMAL, AlertLoudness.of(Float.NaN))
    }

    // ── the venue ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a plain price alert can be asked of the server`() {
        val draft = AlertDraft(
            symbol = "btc/usdt",
            conditions = listOf(AlertConditionDraft(priceOp = PriceOp.GREATER_THAN, first = "68500")),
            frequency = AlertFrequency.ONCE,
            venue = AlertVenue.SERVER,
        )

        val request = draft.serverRequest()

        assertEquals(
            ServerAlertRequest("BTC/USDT", PriceAlertCondition.ABOVE, 68_500.0, PriceAlertTrigger.ONCE),
            request,
        )
        assertTrue(draft.valid)
    }

    @Test
    fun `a condition the server cannot state is refused rather than approximated`() {
        // The failure this prevents is not a refusal, it is an alert that quietly became a
        // *different* alert on the way to the server and then fired, correctly, for a condition
        // nobody asked about.
        val channel = AlertDraft(
            symbol = "BTCUSDT",
            conditions = listOf(
                AlertConditionDraft(kind = AlertTriggerKind.CHANNEL, first = "60000", second = "70000"),
            ),
        )
        val watchlist = AlertDraft(
            symbol = "BTCUSDT",
            scopeListId = "movers",
            conditions = listOf(AlertConditionDraft(first = "68500")),
        )
        val compound = AlertDraft(
            symbol = "BTCUSDT",
            conditions = listOf(AlertConditionDraft(first = "1"), AlertConditionDraft(first = "2")),
        )

        assertNull(channel.serverRequest())
        assertNull(watchlist.serverRequest())
        assertNull(compound.serverRequest())
        assertFalse("a server venue on an unsayable condition must not save", compound.copy(venue = AlertVenue.SERVER).valid)
    }
}
