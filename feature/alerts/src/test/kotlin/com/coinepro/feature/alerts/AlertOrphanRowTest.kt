package com.coinepro.feature.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.coinepro.core.datastore.AlertAuditStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.StoredDrawing
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.PriceOp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An alert whose drawing is gone, in the list.
 *
 * Before this the row was indistinguishable from a live one: it sat under «فعال», the reader had
 * no reason to look at it, and it could never fire. The assertions that matter most here are the
 * two negative ones — nothing is marked while the drawings have not been read, and an alert that
 * watches a price is never given a drawing at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertOrphanRowTest {

    private fun drawingAlert(id: String, drawingId: String, symbol: String = "BTCUSDT") =
        LocalPriceAlert(
            id = id,
            symbol = symbol,
            condition = LocalAlertCondition.ABOVE,
            value = 1.0,
            trigger = AlertTrigger.DrawingTouch(drawingId),
            createdAtEpochMillis = NOW,
        )

    private fun stored(id: Long, toolId: String = "trend") = StoredDrawing(
        id = id,
        toolId = toolId,
        points = listOf(1_700_000_000L to 100.0, 1_700_003_600L to 110.0),
        colour = 0xFFD4AF37L,
        widthDp = 2f,
        text = null,
        direction = "",
    )

    @Test
    fun `an alert whose drawing is still there carries its name and its anchors`() = runTest {
        val store = store()
        store.add(drawingAlert("a", drawingId = "7"))
        val controller = controller(store, drawings = mapOf("BTCUSDT" to listOf(stored(7))))

        val row = onlyRow(controller)

        assertFalse(row.orphaned)
        assertEquals("خط روند", row.drawing?.label)
        assertEquals(2, row.drawing?.points?.size)
    }

    @Test
    fun `an alert whose drawing was deleted says so`() = runTest {
        val store = store()
        store.add(drawingAlert("a", drawingId = "7"))
        val controller = controller(store, drawings = mapOf("BTCUSDT" to emptyList()))

        val row = onlyRow(controller)

        assertTrue(row.orphaned)
        // Nothing to sketch, and no invented name for a drawing nobody can look at.
        assertTrue(row.drawing?.points.orEmpty().isEmpty())
    }

    @Test
    fun `nothing is marked while the drawings have not been read`() = runTest {
        // The first frame of the screen. Marking the whole list broken here would be a worse lie
        // than the silence it replaces.
        val store = store()
        store.add(drawingAlert("a", drawingId = "7"))
        val controller = controller(store, drawings = emptyMap())

        val row = onlyRow(controller)

        assertFalse(row.orphaned)
        assertNull(row.drawing)
    }

    @Test
    fun `an alert on a price is never given a drawing`() = runTest {
        val store = store()
        store.add(
            LocalPriceAlert(
                id = "a",
                symbol = "BTCUSDT",
                condition = LocalAlertCondition.ABOVE,
                value = 70_000.0,
                trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 70_000.0),
                createdAtEpochMillis = NOW,
            ),
        )
        val controller = controller(store, drawings = mapOf("BTCUSDT" to emptyList()))

        val row = onlyRow(controller)

        assertNull(row.drawing)
        assertFalse(row.orphaned)
    }

    @Test
    fun `one missing term of an AND orphans the alert`() = runTest {
        val store = store()
        store.add(
            drawingAlert("a", drawingId = "7").copy(
                trigger = AlertTrigger.MultiCondition(
                    listOf(AlertTrigger.DrawingTouch("7"), AlertTrigger.DrawingTouch("8")),
                ),
            ),
        )
        val controller = controller(store, drawings = mapOf("BTCUSDT" to listOf(stored(7))))

        assertTrue(onlyRow(controller).orphaned)
    }

    @Test
    fun `a reader with no drawing alerts never has their drawings read`() = runTest {
        val store = store()
        store.add(
            LocalPriceAlert(
                id = "a",
                symbol = "BTCUSDT",
                condition = LocalAlertCondition.ABOVE,
                value = 70_000.0,
                createdAtEpochMillis = NOW,
            ),
        )
        val asked = mutableListOf<String>()
        controller(store, drawings = emptyMap(), asked = asked)

        assertTrue(asked.isEmpty())
    }

    @Test
    fun `the drawings of a symbol with an alert on it are read once`() = runTest {
        val store = store()
        store.add(drawingAlert("a", drawingId = "7"))
        store.add(drawingAlert("b", drawingId = "8"))
        val asked = mutableListOf<String>()
        controller(store, drawings = mapOf("BTCUSDT" to listOf(stored(7), stored(8))), asked = asked)

        // Two alerts, one symbol, one read — and the second `add` does not re-read it, because the
        // watch is keyed on the *set of symbols* rather than on the alert list.
        assertEquals(listOf("BTCUSDT"), asked)
    }

    private fun TestScope.controller(
        store: LocalAlertStore,
        drawings: Map<String, List<StoredDrawing>>,
        asked: MutableList<String> = mutableListOf(),
    ) = AlertsController(
        store = store,
        audit = AlertAuditStore(FakeOrphanPreferences()),
        catalogOf = { listOf("BTCUSDT") },
        scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        timeframeOf = { null },
        forgetFireState = { },
        drawingsOf = { symbol ->
            asked += symbol
            drawings[symbol] ?: error("no drawings read for $symbol")
        },
        now = { NOW },
        newId = { "new0000000000000" },
    )

    private fun store() = LocalAlertStore(FakeOrphanPreferences())

    private fun onlyRow(controller: AlertsController): AlertRow =
        controller.state.value.sections.flatMap(AlertRowSection::rows).first()

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}

private class FakeOrphanPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(data.value)
        data.value = next
        return next
    }
}
