package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.database.SavedScriptDao
import com.coinepro.core.database.SavedScriptEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Save as mine», end to end through the row (4.82.0, run Σ item S3 C).
 *
 * The model's own behaviour is `ScriptDocumentTest`'s in `:namascript`. What is held here is the
 * part that can lose a reader's work: what survives a save, what a save does to the history, and
 * what happens when the thing they saved is reopened a week later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScriptDocumentControllerTest {

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
        List(200) { index ->
            val base = 100.0 + index % 11
            Candle(t = 1_700_000_000L + index * 3_600L, o = base, h = base + 2, l = base - 2, c = base + 1, v = 100.0)
        },
    )

    private lateinit var dao: FakeDao

    private fun controller(scope: TestScope): ScriptController {
        dao = FakeDao()
        return ScriptController(dao, TestScope(UnconfinedTestDispatcher(scope.testScheduler))).also { it.setSeries(series) }
    }

    private fun ScriptController.dress() {
        describe("میانگینی که خودم ساختم")
        setColour(0xFF2962FF)
        setTags("روند، میانگین")
        setOwnPane(true)
    }

    @Test
    fun `everything the reader chose survives a save and a reopen`() = runTest {
        val controller = controller(this)
        controller.openText(name = "میانگین من", source = "plot(ta.ema(close, 20))")
        controller.dress()
        controller.save()

        val row = dao.rows.value.single()
        assertEquals("میانگینی که خودم ساختم", row.description)
        assertEquals(0xFF2962FF, row.colour)
        assertEquals("روند, میانگین", row.tags)
        assertTrue(row.ownPane)

        // …and a week later.
        controller.close()
        controller.open(row)
        val state = controller.state.value
        assertEquals("میانگینی که خودم ساختم", state.description)
        assertEquals(0xFF2962FF, state.colour)
        assertEquals(listOf("روند", "میانگین"), state.tags)
        assertTrue(state.ownPane)
    }

    @Test
    fun `a tag list is trimmed, emptied and split on either comma`() {
        // A Persian keyboard writes «،» and an English one writes «,», and a reader should not have
        // to know which one this field wanted.
        val controller = ScriptController(FakeDao(), TestScope(UnconfinedTestDispatcher()))
        controller.setTags("  روند ،, , میانگین  ,")
        assertEquals(listOf("روند", "میانگین"), controller.state.value.tags)
    }

    @Test
    fun `a save that changed the source keeps the version it replaced`() = runTest {
        val controller = controller(this)
        controller.openText(name = "mine", source = "plot(close)")
        controller.save()
        controller.edit("plot(ta.ema(close, 20))")
        controller.save()

        val row = dao.rows.value.single()
        assertEquals("plot(ta.ema(close, 20))", row.source)
        val history = controller.state.value.history
        assertEquals(1, history.size)
        assertEquals("plot(close)", history.first().source)
    }

    @Test
    fun `a save that changed nothing does not spend a revision`() = runTest {
        // The reader who opens a script, reads it and presses save out of habit. Five revisions of
        // an identical script is a history that has lost what it was kept for.
        val controller = controller(this)
        controller.openText(name = "mine", source = "plot(close)")
        controller.save()
        controller.save()
        controller.save()
        assertTrue(controller.state.value.history.isEmpty())
    }

    @Test
    fun `only the last five are kept, and they survive a reopen`() = runTest {
        val controller = controller(this)
        controller.openText(name = "mine", source = "plot(close + 0)")
        controller.save()
        for (revision in 1..9) {
            controller.edit("plot(close + $revision)")
            controller.save()
        }
        assertEquals(ScriptDocument.REVISIONS, controller.state.value.history.size)

        val row = dao.rows.value.single()
        controller.close()
        controller.open(row)
        val history = controller.state.value.history
        assertEquals(ScriptDocument.REVISIONS, history.size)
        assertEquals("plot(close + 8)", history.first().source)
        assertEquals("plot(close + 4)", history.last().source)
    }

    @Test
    fun `restoring a version puts it in the editor and does not write it`() = runTest {
        // Looking through a history is looking. A restore that saved itself would make «what did
        // this used to say» a destructive question.
        val controller = controller(this)
        controller.openText(name = "mine", source = "plot(close)")
        controller.save()
        controller.edit("plot(ta.rsi(close, 14))")
        controller.save()

        controller.restore(controller.state.value.history.first())
        assertEquals("plot(close)", controller.state.value.source)
        assertEquals("the restore wrote itself to the row", "plot(ta.rsi(close, 14))", dao.rows.value.single().source)
    }

    @Test
    fun `a history survives a script with every awkward character in it`() = runTest {
        // The encoding is two control characters and no escaping, which is only safe because the
        // language cannot contain them. Everything else — quotes, brackets, newlines, Persian — has
        // to pass through untouched.
        // The control characters included: `ScriptHistoryTest` shows a string literal can carry
        // one, and the old separator-based encoding lost the rest of the history when it did.
        val awkward = "note = \"«۳ کندل», a\u001eb\u001fc, 12:34\"\nplot(close, title = note)\n"
        val controller = controller(this)
        controller.openText(name = "mine", source = awkward)
        controller.save()
        controller.edit("plot(close)")
        controller.save()

        val row = dao.rows.value.single()
        controller.close()
        controller.open(row)
        assertEquals(awkward, controller.state.value.history.single().source)
    }

    @Test
    fun `a script written before the new columns opens with sensible defaults`() = runTest {
        // The migration fills the columns; this is the other half — that nothing in the editor
        // trips over a script that predates any of them.
        val controller = controller(this)
        val old = SavedScriptEntity(
            id = 7,
            name = "قدیمی",
            source = "plot(close)",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        controller.open(old)
        val state = controller.state.value
        assertEquals("", state.description)
        assertEquals(ScriptDocument.DEFAULT_COLOUR, state.colour)
        assertEquals(emptyList<String>(), state.tags)
        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `the editor can hand out a document, and take one back`() = runTest {
        val controller = controller(this)
        controller.openText(name = "میانگین من", source = "plot(ta.ema(close, 20))")
        controller.dress()

        val document = controller.document()
        val written = ScriptFile.write(document)
        val read = ScriptFile.read(written)
        assertNotNull(read)

        controller.close()
        controller.openDocument(read!!)
        val state = controller.state.value
        assertEquals("میانگین من", state.name)
        assertEquals("plot(ta.ema(close, 20))", state.source)
        assertEquals(0xFF2962FF, state.colour)
        assertEquals(listOf("روند", "میانگین"), state.tags)
        assertTrue(state.ownPane)
        // An import is a new script until the reader says otherwise: it must not overwrite a row.
        assertNull(state.savedId)
    }

    @Test
    fun `an imported script can be shared back at the same address`() = runTest {
        val controller = controller(this)
        controller.openText(name = "mine", source = "plot(close)")
        val original = controller.document()
        controller.close()
        controller.openDocument(original)
        assertEquals(original.id, controller.state.value.publicId)
        assertEquals(original.id, ScriptLink.idOf(ScriptLink.of(controller.document().id)))
    }
}
