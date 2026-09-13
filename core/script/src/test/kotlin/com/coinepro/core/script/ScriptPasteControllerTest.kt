package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.database.SavedScriptDao
import com.coinepro.core.database.SavedScriptEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the editor does with a paste (run Σ, S3 A).
 *
 * The repairs themselves are `ScriptPasteTest`'s in `:namascript`. What is held here is the part a
 * reader can lose work to: a paste replaces the editor, and the thing it replaced must be
 * recoverable in one tap — otherwise the fixes have to be read carefully before they are accepted,
 * which is exactly the burden this feature exists to remove.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScriptPasteControllerTest {

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

    private val series = CandleSeries(
        List(240) { index ->
            val base = 100.0 + index % 13 - (index % 7)
            Candle(t = 1_700_000_000L + index * 3_600L, o = base, h = base + 2, l = base - 2, c = base + 1, v = 100.0)
        },
    )

    private fun controller(scope: TestScope): ScriptController =
        ScriptController(FakeDao(), TestScope(UnconfinedTestDispatcher(scope.testScheduler))).also { it.setSeries(series) }

    @Test
    fun `a pasted script arrives repaired, with the repairs listed`() = runTest {
        val controller = controller(this)
        controller.paste("fast = ema(close, 9)\nplot(fast, \"Fast\")")
        val state = controller.state.value
        assertTrue("nothing was repaired", state.paste?.fixes.orEmpty().isNotEmpty())
        assertTrue("the bare name kept its shape: ${state.source}", "ta.ema(" in state.source)
        assertTrue("the positional title was left: ${state.source}", "title = \"Fast\"" in state.source)
        assertNull("the repaired script does not compile", NamaScript.check(state.source))
    }

    @Test
    fun `undo puts back exactly what was pasted`() = runTest {
        val controller = controller(this)
        val pasted = "fast = ema(close, 9)\nplot(fast, \"Fast\")"
        controller.paste(pasted)
        assertTrue(controller.state.value.source != pasted)
        controller.undoPaste()
        assertEquals(pasted, controller.state.value.source)
        assertNull("the banner outlived the undo", controller.state.value.paste)
    }

    @Test
    fun `a paste does not overwrite the saved script it landed on`() = runTest {
        // The failure this guards: a reader opens a saved script, pastes a new one over it, presses
        // save, and the script they had is gone. A paste is a new script until they say otherwise.
        val controller = controller(this)
        controller.openText(name = "mine", source = "plot(close)")
        controller.save()
        val savedId = controller.state.value.savedId
        assertNotNull(savedId)
        controller.paste("plot(ta.rsi(close, 14), pane = \"own\")")
        assertNull("the paste kept the saved row's id", controller.state.value.savedId)
    }

    @Test
    fun `a paste does not run on its own`() = runTest {
        // Running a stranger's code because it reached the clipboard is not a decision the reader
        // made. «اجرا» is one tap away and it is theirs.
        val controller = controller(this)
        controller.paste("plot(ta.ema(close, 20))")
        assertNull("the paste ran itself", controller.state.value.result)
    }

    @Test
    fun `a sentence leaves the editor alone and offers templates`() = runTest {
        val controller = controller(this)
        controller.openText(name = "mine", source = "plot(close)")
        controller.paste("وقتی میانگین ۲۰ از ۵۰ رد شد بخر")
        assertEquals("the sentence was written into the editor", "plot(close)", controller.state.value.source)
        assertEquals(ScriptPaste.Dialect.PROSE, controller.state.value.paste?.dialect)
        assertTrue(controller.state.value.paste?.templates.orEmpty().isNotEmpty())
    }

    @Test
    fun `taking a template fills it with the numbers the sentence named`() = runTest {
        val controller = controller(this)
        controller.paste("وقتی میانگین ۹ از ۲۱ رد شد بخر")
        val template = controller.state.value.paste?.templates?.first { it.id == "ma-cross" }
        assertNotNull(template)
        controller.openTemplate(template!!)
        val state = controller.state.value
        assertTrue("the reader's 9 was dropped: ${state.source}", "input(9," in state.source)
        assertTrue("the reader's 21 was dropped: ${state.source}", "input(21," in state.source)
        assertNull("the banner stayed up after the choice was made", state.paste)
        assertNull("the template does not compile", NamaScript.check(state.source))
    }

    @Test
    fun `a clean paste says so rather than saying nothing`() = runTest {
        val controller = controller(this)
        controller.paste("average = ta.ema(close, 20)\nplot(average, title = \"a\")")
        val paste = controller.state.value.paste
        assertNotNull(paste)
        assertTrue("a clean paste claimed a repair", paste!!.fixes.isEmpty())
        assertEquals(ScriptPaste.Dialect.NAMA, paste.dialect)
    }

    @Test
    fun `dismissing keeps the repaired text`() = runTest {
        val controller = controller(this)
        controller.paste("fast = ema(close, 9)\nplot(fast)")
        val repaired = controller.state.value.source
        controller.dismissPaste()
        assertEquals(repaired, controller.state.value.source)
        assertNull(controller.state.value.paste)
    }

    @Test
    fun `undo after a dismiss does nothing rather than throwing`() = runTest {
        val controller = controller(this)
        controller.paste("fast = ema(close, 9)\nplot(fast)")
        val repaired = controller.state.value.source
        controller.dismissPaste()
        controller.undoPaste()
        assertEquals(repaired, controller.state.value.source)
    }
}
