package com.coinepro.app.alerts

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.AuditEvent
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.PriceOp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the evaluator does, checked against the four ways an alerts feature actually fails people.
 *
 * Firing twice for one bar, one watchlist member consuming the alert for the rest, a delivery that
 * failed and left no trace, and a dropped network call quietly spending an alert somebody had been
 * waiting weeks for. None of them is visible in a screenshot and all of them are one assertion each
 * here.
 */
class AlertEvaluatorTest {

    @Test
    fun `a once-per-bar alert does not fire twice inside the same bar`() = runTest {
        val alert = alert(
            trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0),
            frequency = AlertFrequency.ONCE_PER_BAR,
        )
        val world = World(listOf(alert))
        world.samples = mapOf("BTCUSDT" to sample(price = 105.0, barStart = 10_000L))

        assertEquals(AlertPassResult.Completed(fired = 1, expired = 0), world.evaluator.evaluate(10_500L))
        assertEquals(AlertPassResult.Idle, world.evaluator.evaluate(10_800L))

        // A new bar re-arms it, which is the other half of "once per bar" and the half that is
        // silently lost if the stamp is compared against a clock instead of against the bar.
        world.samples = mapOf("BTCUSDT" to sample(price = 105.0, barStart = 20_000L))
        assertEquals(AlertPassResult.Completed(fired = 1, expired = 0), world.evaluator.evaluate(20_100L))
        assertEquals(2, world.deliverer.delivered.size)
    }

    @Test
    fun `one watchlist member firing does not silence the others`() = runTest {
        val alert = alert(
            trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0),
            scope = AlertScope.Watchlist(AlertScope.Watchlist.DEFAULT_LIST_ID),
            frequency = AlertFrequency.ONCE,
        )
        val world = World(listOf(alert))
        world.listMembers = mapOf(AlertScope.Watchlist.DEFAULT_LIST_ID to listOf("BTCUSDT", "ETHUSDT"))
        world.samples = mapOf(
            "BTCUSDT" to sample(price = 105.0),
            "ETHUSDT" to sample(price = 50.0, symbol = "ETHUSDT"),
        )
        world.evaluator.evaluate(1_000L)
        assertEquals(listOf("BTCUSDT"), world.deliverer.delivered.map(FiredAlert::symbol))

        world.samples = mapOf(
            "BTCUSDT" to sample(price = 106.0),
            "ETHUSDT" to sample(price = 105.0, symbol = "ETHUSDT"),
        )
        world.evaluator.evaluate(2_000L)
        assertEquals(listOf("BTCUSDT", "ETHUSDT"), world.deliverer.delivered.map(FiredAlert::symbol))

        // And the alert row itself is untouched, so a member added to the list tomorrow is still
        // covered. Stamping the row would have deactivated a one-shot on the first member.
        assertTrue(world.repository.alerts.single().active)
        assertNull(world.repository.alerts.single().lastFiredAtEpochMillis)
    }

    @Test
    fun `a firing that reaches the reader is recorded as fired and then delivered`() = runTest {
        val world = World(listOf(alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0))))
        world.samples = mapOf("BTCUSDT" to sample(price = 105.0))

        world.evaluator.evaluate(1_000L)

        assertEquals(listOf(AuditEvent.FIRED, AuditEvent.DELIVERED), world.audit.entries.map(AlertAuditEntry::event))
        val fired = world.audit.entries.first()
        assertEquals("BTCUSDT", fired.symbol)
        assertEquals(105.0, fired.price ?: 0.0, 1e-9)
        assertEquals("H1", fired.timeframe)
    }

    @Test
    fun `a firing that could not be delivered says so, with the reason`() = runTest {
        val world = World(listOf(alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0))))
        world.samples = mapOf("BTCUSDT" to sample(price = 105.0))
        world.deliverer.outcome = { AlertDeliveryOutcome.Failed("اجازه‌ی اعلان داده نشده است") }

        world.evaluator.evaluate(1_000L)

        assertEquals(
            listOf(AuditEvent.FIRED, AuditEvent.DELIVERY_FAILED),
            world.audit.entries.map(AlertAuditEntry::event),
        )
        assertEquals("اجازه‌ی اعلان داده نشده است", world.audit.entries.last().note)
    }

    @Test
    fun `a deliverer that throws is still written down as a failure`() = runTest {
        val world = World(listOf(alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0))))
        world.samples = mapOf("BTCUSDT" to sample(price = 105.0))
        world.deliverer.outcome = { throw IllegalStateException("notification manager refused") }

        world.evaluator.evaluate(1_000L)

        assertEquals(
            listOf(AuditEvent.FIRED, AuditEvent.DELIVERY_FAILED),
            world.audit.entries.map(AlertAuditEntry::event),
        )
        assertEquals("notification manager refused", world.audit.entries.last().note)
    }

    @Test
    fun `an alert that has passed its own expiry records that once and only once`() = runTest {
        val world = World(
            listOf(alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0), expiresAt = 500L)),
        )
        world.samples = mapOf("BTCUSDT" to sample(price = 105.0))

        world.evaluator.evaluate(1_000L)
        world.evaluator.evaluate(2_000L)
        world.evaluator.evaluate(3_000L)

        assertEquals(listOf(AuditEvent.EXPIRED), world.audit.entries.map(AlertAuditEntry::event))
        assertNotNull(world.fireStates.states.getValue("a1").expiredRecordedAt)
        assertTrue(world.deliverer.delivered.isEmpty())
    }

    @Test
    fun `a market that could not be read leaves every alert exactly as armed as it was`() = runTest {
        val alert = alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0))
        val world = World(listOf(alert))
        world.failure = AppResult.Failure(ErrorKind.NETWORK, "timed out")

        val result = world.evaluator.evaluate(1_000L)

        assertEquals(AlertPassResult.Unavailable("timed out"), result)
        assertTrue("nothing may be written down about a pass that decided nothing", world.audit.entries.isEmpty())
        assertTrue(world.fireStates.states.isEmpty())
        assertTrue(world.deliverer.delivered.isEmpty())
        assertEquals(alert, world.repository.alerts.single())
    }

    @Test
    fun `a plain one-shot alert is still stamped on its own row`() = runTest {
        val world = World(
            listOf(
                alert(
                    trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0),
                    repeat = AlertRepeat.ONCE,
                ),
            ),
        )
        world.samples = mapOf("BTCUSDT" to sample(price = 105.0))

        world.evaluator.evaluate(1_000L)

        val stored = world.repository.alerts.single()
        assertEquals(1_000L, stored.lastFiredAtEpochMillis ?: 0L)
        assertTrue("a one-shot deactivates itself", !stored.active)
        assertEquals(AlertPassResult.Idle, world.evaluator.evaluate(2_000L))
    }

    @Test
    fun `only the symbols with an alert on them are ever asked for`() = runTest {
        val world = World(
            listOf(
                alert(id = "a1", symbol = "BTCUSDT", trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 1.0)),
                alert(id = "a2", symbol = "ETHUSDT", trigger = AlertTrigger.Price(PriceOp.CROSSING_UP, 1.0)),
            ),
        )
        world.samples = emptyMap()

        world.evaluator.evaluate(1_000L)

        assertEquals(listOf("BTCUSDT", "ETHUSDT"), world.market.asked.map(AlertMarketRequest::symbol))
        // The crossing needs a previous sample and the plain comparison does not, and that is the
        // difference between one number and a few hundred bars over the wire.
        assertTrue(!world.market.asked.first().needs.candles)
        assertTrue(world.market.asked.last().needs.candles)
    }

    // ── Fakes ────────────────────────────────────────────────────────────────────────────

    private class FakeRepository(var alerts: List<LocalPriceAlert>) : AlertRepository {
        override suspend fun all(): List<LocalPriceAlert> = alerts

        override suspend fun markFired(fired: List<LocalPriceAlert>, atEpochMillis: Long) {
            val ids = fired.mapTo(mutableSetOf(), LocalPriceAlert::id)
            alerts = alerts.map { if (it.id in ids) it.fired(atEpochMillis) else it }
        }
    }

    private class FakeFireStates : AlertFireStates {
        val states = mutableMapOf<String, AlertFireState>()
        override suspend fun current(): Map<String, AlertFireState> = states.toMap()
        override suspend fun write(states: List<AlertFireState>) {
            states.forEach { state -> this.states[state.alertId] = state }
        }
    }

    private class FakeAudit : AlertAuditLog {
        val entries = mutableListOf<AlertAuditEntry>()
        override suspend fun record(entries: List<AlertAuditEntry>) {
            this.entries.addAll(entries)
        }
    }

    private class FakeDeliverer : AlertDeliverer {
        var outcome: (FiredAlert) -> AlertDeliveryOutcome = { AlertDeliveryOutcome.Delivered }
        val delivered = mutableListOf<FiredAlert>()
        override suspend fun deliver(fired: FiredAlert): AlertDeliveryOutcome {
            delivered += fired
            return outcome(fired)
        }
    }

    private class FakeMarket : AlertMarketSource {
        var samples: Map<String, AlertSample> = emptyMap()
        var failure: AppResult.Failure? = null
        var asked: List<AlertMarketRequest> = emptyList()

        override suspend fun read(requests: List<AlertMarketRequest>): AppResult<Map<String, AlertSample>> {
            asked = requests
            return failure ?: AppResult.Success(samples)
        }
    }

    /** One assembled world, so each test says what it is about rather than wiring six fakes. */
    private class World(alerts: List<LocalPriceAlert>) {
        val repository = FakeRepository(alerts)
        val fireStates = FakeFireStates()
        val audit = FakeAudit()
        val deliverer = FakeDeliverer()
        val market = FakeMarket()
        var listMembers: Map<String, List<String>> = emptyMap()

        var samples: Map<String, AlertSample>
            get() = market.samples
            set(value) {
                market.samples = value
            }

        var failure: AppResult.Failure?
            get() = market.failure
            set(value) {
                market.failure = value
            }

        val evaluator = AlertEvaluator(
            alerts = repository,
            membership = object : AlertMembership {
                override suspend fun members(listId: String): List<String> = listMembers[listId].orEmpty()
            },
            fireStates = fireStates,
            market = market,
            audit = audit,
            deliverer = deliverer,
        )
    }

    private companion object {

        fun alert(
            id: String = "a1",
            symbol: String = "BTCUSDT",
            trigger: AlertTrigger? = null,
            repeat: AlertRepeat = AlertRepeat.ALWAYS,
            frequency: AlertFrequency? = null,
            scope: AlertScope? = null,
            expiresAt: Long? = null,
        ) = LocalPriceAlert(
            id = id,
            symbol = symbol,
            condition = LocalAlertCondition.ABOVE,
            value = 0.0,
            repeat = repeat,
            trigger = trigger,
            scope = scope,
            frequency = frequency,
            expiresAt = expiresAt,
        )

        fun sample(
            price: Double,
            symbol: String = "BTCUSDT",
            previous: Double? = null,
            barStart: Long = 0L,
        ) = AlertSample(
            symbol = symbol,
            price = price,
            previousPrice = previous,
            changePercent24h = null,
            barStart = barStart,
            timeframe = "H1",
        )
    }
}
