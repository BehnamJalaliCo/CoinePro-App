package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.database.SavedScriptDao
import com.coinepro.core.database.SavedScriptEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptControllerTest {

    private fun series(count: Int = 120): CandleSeries = CandleSeries(
        List(count) { index ->
            val close = 100.0 + index * 0.4
            Candle(1_700_000_000L + index * 3_600L, close - 0.3, close + 0.8, close - 0.9, close, 1_000.0)
        },
    )

    private class FakeDao : SavedScriptDao {
        val rows = MutableStateFlow<List<SavedScriptEntity>>(emptyList())
        private var nextId = 1L

        override fun scripts(): Flow<List<SavedScriptEntity>> = rows
        override suspend fun byId(id: Long): SavedScriptEntity? = rows.value.firstOrNull { it.id == id }
        override suspend fun count(): Int = rows.value.size
        override suspend fun insert(script: SavedScriptEntity): Long {
            val id = nextId++
            rows.value = rows.value + script.copy(id = id)
            return id
        }
        override suspend fun update(script: SavedScriptEntity) {
            rows.value = rows.value.map { if (it.id == script.id) script else it }
        }
        override suspend fun delete(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }

    /**
     * A controller on a scope this test owns.
     *
     * Not the `runTest` scope: the controller keeps a `stateIn` collector running for the life of
     * its scope, and `runTest` waits for its children — so handing it the test's own scope hangs
     * the test rather than failing it. Cancelling here is what ends that collector.
     */
    private fun TestScope.withController(block: (ScriptController, FakeDao) -> Unit) {
        val dao = FakeDao()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = ScriptController(dao, scope, now = { 1_700_000_000_000L })
        controller.setSeries(series())
        try {
            block(controller, dao)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `running a script produces plots`() = runTest(StandardTestDispatcher()) {
        withController { controller, _ ->
            controller.edit("plot(ta.ema(close, 10))")
            controller.run()
            testScheduler.advanceUntilIdle()

            assertEquals(1, controller.state.value.result?.plots?.size)
            assertFalse(controller.state.value.dirty)
        }
    }

    @Test
    fun `a syntax error is found while typing, without running`() = runTest(StandardTestDispatcher()) {
        withController { controller, _ ->
            controller.edit("plot(ta.ema(close, 10)")
            testScheduler.advanceUntilIdle()

            // No run happened, so there is no result — and the caret still has somewhere to go.
            assertNull(controller.state.value.result)
            assertNotNull(controller.state.value.syntax)
            assertTrue(controller.state.value.failure!!.line > 0)
        }
    }

    @Test
    fun `saving twice updates one row rather than making two`() = runTest(StandardTestDispatcher()) {
        withController { controller, dao ->
            controller.edit("plot(close)")
            controller.rename("مال من")
            controller.save()
            testScheduler.advanceUntilIdle()
            controller.edit("plot(close * 2)")
            controller.save()
            testScheduler.advanceUntilIdle()

            assertEquals(1, dao.rows.value.size)
            assertEquals("plot(close * 2)", dao.rows.value.single().source)
        }
    }

    @Test
    fun `saving a copy leaves the original alone`() = runTest(StandardTestDispatcher()) {
        withController { controller, dao ->
            controller.edit("plot(close)")
            controller.rename("اصلی")
            controller.save()
            testScheduler.advanceUntilIdle()
            controller.edit("plot(high)")
            controller.saveAsCopy()
            testScheduler.advanceUntilIdle()

            assertEquals(2, dao.rows.value.size)
            assertEquals("plot(close)", dao.rows.value.first { it.name == "اصلی" }.source)
        }
    }

    @Test
    fun `an unnamed script is saved under a name rather than refused`() = runTest(StandardTestDispatcher()) {
        withController { controller, dao ->
            controller.edit("plot(close)")
            controller.save()
            testScheduler.advanceUntilIdle()

            assertEquals(1, dao.rows.value.size)
            assertTrue(dao.rows.value.single().name.isNotBlank())
        }
    }

    @Test
    fun `an input override survives a save and a reopen`() = runTest(StandardTestDispatcher()) {
        withController { controller, dao ->
            controller.edit("length = input(14, title = \"طول\", min = 2, max = 100)\nplot(ta.ema(close, length))")
            controller.run()
            testScheduler.advanceUntilIdle()
            controller.setInput("طول", 40.0)
            testScheduler.advanceUntilIdle()
            controller.save()
            testScheduler.advanceUntilIdle()

            val stored = dao.rows.value.single()
            controller.close()
            controller.open(stored)
            testScheduler.advanceUntilIdle()

            assertEquals(40.0, controller.state.value.overrides["طول"]!!, 1e-9)
            assertEquals(40.0, controller.state.value.result!!.inputs.single().value, 1e-9)
        }
    }

    @Test
    fun `an override for an input the script no longer declares is dropped`() = runTest(StandardTestDispatcher()) {
        withController { controller, _ ->
            controller.edit("length = input(14, title = \"طول\")\nplot(ta.ema(close, length))")
            controller.run()
            testScheduler.advanceUntilIdle()
            controller.setInput("طول", 30.0)
            testScheduler.advanceUntilIdle()

            controller.edit("plot(close)")
            controller.run()
            testScheduler.advanceUntilIdle()

            assertTrue(controller.state.value.overrides.isEmpty())
        }
    }

    @Test
    fun `deleting the open script does not empty the editor`() = runTest(StandardTestDispatcher()) {
        withController { controller, dao ->
            controller.edit("plot(close)")
            controller.save()
            testScheduler.advanceUntilIdle()
            val id = dao.rows.value.single().id

            controller.delete(id)
            testScheduler.advanceUntilIdle()

            // Unsaved edits belong to the reader, not to the row.
            assertEquals("plot(close)", controller.state.value.source)
            assertNull(controller.state.value.savedId)
        }
    }

    @Test
    fun `a preset opens, runs and remembers where it came from`() = runTest(StandardTestDispatcher()) {
        withController { controller, _ ->
            val preset = ScriptPresets.byId("rsi-zones")!!
            controller.openPreset(preset)
            testScheduler.advanceUntilIdle()

            assertEquals("rsi-zones", controller.state.value.presetId)
            assertNull(controller.state.value.result?.error)
            assertTrue(controller.state.value.result!!.plots.isNotEmpty())
        }
    }

    @Test
    fun `a blank source is never saved`() = runTest(StandardTestDispatcher()) {
        withController { controller, dao ->
            controller.edit("   ")
            controller.save()
            testScheduler.advanceUntilIdle()

            assertTrue(dao.rows.value.isEmpty())
        }
    }
}
