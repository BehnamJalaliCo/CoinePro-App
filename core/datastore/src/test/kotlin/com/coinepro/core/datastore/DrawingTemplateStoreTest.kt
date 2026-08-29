package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawingTemplateStoreTest {

    private fun template(
        id: String = "t1",
        toolId: String = "trend",
        name: String = "حمایت روزانه",
        createdAt: Long = 1_700_000_000_000L,
    ) = DrawingTemplate(
        id = id,
        toolId = toolId,
        name = name,
        colour = 0xFF4CAF50,
        widthDp = 2f,
        createdAt = createdAt,
    )

    @Test
    fun `a template round-trips with every field intact`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template())

        assertEquals(template(), store.all().first().single())
    }

    @Test
    fun `templates are listed per tool and never bleed across tools`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template(id = "a", toolId = "trend"))
        store.save(template(id = "b", toolId = "rect"))

        assertEquals(listOf("a"), store.templates("trend").first().map(DrawingTemplate::id))
        assertEquals(listOf("b"), store.templates("rect").first().map(DrawingTemplate::id))
        assertEquals(2, store.all().first().size)
    }

    @Test
    fun `saving the same id twice replaces the row rather than appending a second`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template(name = "اول"))
        store.save(template(name = "دوم"))

        assertEquals("دوم", store.all().first().single().name)
    }

    @Test
    fun `a template with a blank id or tool id is refused rather than stored unusable`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template(id = " "))
        store.save(template(id = "ok", toolId = ""))

        assertTrue(store.all().first().isEmpty())
    }

    @Test
    fun `a short row written by an older build decodes with its missing fields defaulted`() = runTest {
        val backing = FakeTemplatePreferences()
        // Two fields only: an id and a tool id, which is what a build before colour, width and a
        // creation time would have written. It must read back, not throw and not vanish.
        backing.data.value = mutablePreferencesOf(
            stringPreferencesKey("drawing_templates") to "old\u001Etrend",
        )
        val store = DrawingTemplateStore(backing)

        val restored = store.all().first().single()
        assertEquals("old", restored.id)
        assertEquals("trend", restored.toolId)
        assertEquals("", restored.name)
        assertEquals(DrawingTemplateStore.DEFAULT_COLOUR, restored.colour)
        assertEquals(DrawingTemplateStore.DEFAULT_WIDTH_DP, restored.widthDp, 0.0001f)
        assertEquals(0L, restored.createdAt)
    }

    @Test
    fun `garbage in the stored string is skipped rather than thrown`() = runTest {
        val backing = FakeTemplatePreferences()
        backing.data.value = mutablePreferencesOf(
            stringPreferencesKey("drawing_templates") to "not a record at all",
        )

        // "not a record at all" is one field, and a row with no tool id belongs to nothing.
        assertTrue(DrawingTemplateStore(backing).all().first().isEmpty())
    }

    @Test
    fun `more templates than the cap keep the newest and evict the oldest`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        for (index in 1..250) {
            store.save(template(id = "t$index", createdAt = index.toLong()))
        }

        val all = store.all().first()
        assertEquals(DrawingTemplateStore.MAX_TEMPLATES, all.size)
        assertEquals(250L, all.first().createdAt)
        assertEquals(51L, all.last().createdAt)
    }

    @Test
    fun `renaming changes the name and leaves the creation time alone`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template())
        store.rename("t1", "مقاومت")

        val renamed = store.all().first().single()
        assertEquals("مقاومت", renamed.name)
        assertEquals(template().createdAt, renamed.createdAt)
    }

    @Test
    fun `deleting removes only the template named`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template(id = "a", createdAt = 2L))
        store.save(template(id = "b", createdAt = 1L))
        store.delete("a")

        assertEquals(listOf("b"), store.all().first().map(DrawingTemplate::id))
    }

    @Test
    fun `a tool with no default reads back as null rather than as the first template`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template())

        assertNull(store.defaultFor("trend").first())
    }

    @Test
    fun `a default is resolved to the template it points at`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template())
        store.setDefault("trend", "t1")

        assertEquals(template(), store.defaultFor("trend").first())
        assertNull(store.defaultFor("rect").first())
    }

    @Test
    fun `a default does not survive the deletion of the template it points at`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template())
        store.setDefault("trend", "t1")
        store.delete("t1")

        // A pointer at a row that no longer exists is no default, not a style nobody can see.
        assertNull(store.defaultFor("trend").first())
    }

    @Test
    fun `a null template id clears the default without touching the templates`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template())
        store.setDefault("trend", "t1")
        store.setDefault("trend", null)

        assertNull(store.defaultFor("trend").first())
        assertEquals(1, store.all().first().size)
    }

    @Test
    fun `defaults are kept per tool and one does not overwrite another`() = runTest {
        val store = DrawingTemplateStore(FakeTemplatePreferences())
        store.save(template(id = "a", toolId = "trend"))
        store.save(template(id = "b", toolId = "rect"))
        store.setDefault("trend", "a")
        store.setDefault("rect", "b")

        assertEquals("a", store.defaultFor("trend").first()?.id)
        assertEquals("b", store.defaultFor("rect").first()?.id)
    }

    @Test
    fun `a name carrying a separator is stripped rather than shifting every field after it`() =
        runTest {
            val store = DrawingTemplateStore(FakeTemplatePreferences())
            store.save(template(name = "حمایت\u001Eروزانه"))

            val restored = store.all().first().single()
            assertEquals("حمایتروزانه", restored.name)
            assertEquals(2f, restored.widthDp, 0.0001f)
        }
}

private class FakeTemplatePreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(data.value)
        data.value = next
        return next
    }
}
