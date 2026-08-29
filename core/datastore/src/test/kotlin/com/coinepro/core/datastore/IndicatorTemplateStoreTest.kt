package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IndicatorTemplateStoreTest {

    private fun momentum(id: String = "tpl-1", createdAt: Long = 1_700_000_000_000L) = IndicatorTemplate(
        id = id,
        name = "مومنتوم",
        indicators = listOf("ema", "rsi", "macd"),
        periods = mapOf("ema" to 21, "rsi" to 14),
        sources = mapOf("rsi" to "ema", "macd" to "close"),
        createdAt = createdAt,
    )

    @Test
    fun `a template round-trips with its indicators, periods and sources intact`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())

        store.save(momentum())

        assertEquals(momentum(), store.templates().first().single())
    }

    @Test
    fun `a source is stored as opaque text, so a spelling this build never saw survives`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())

        store.save(momentum().copy(sources = mapOf("rsi" to "ema:21@median")))

        assertEquals(mapOf("rsi" to "ema:21@median"), store.get("tpl-1")?.sources)
    }

    @Test
    fun `a template carries no timeframe or chart type, which is the point of it`() = runTest {
        // Nothing to assert on the type itself — it has no such fields — so this pins the
        // behaviour that follows from that: what comes back is the indicator set and only that.
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())
        store.save(momentum())

        val applied = store.get("tpl-1")!!

        assertEquals(listOf("ema", "rsi", "macd"), applied.indicators)
        assertEquals(mapOf("ema" to 21, "rsi" to 14), applied.periods)
    }

    @Test
    fun `saving the same id twice replaces the template rather than appending a copy`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())
        store.save(momentum())

        store.save(momentum().copy(indicators = listOf("atr")))

        assertEquals(listOf("atr"), store.templates().first().single().indicators)
    }

    @Test
    fun `a template with no indicators is saved, because clearing the chart is a set worth naming`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())

        store.save(IndicatorTemplate(id = "empty", name = "پاک", createdAt = 1L))

        assertEquals(emptyList<String>(), store.get("empty")?.indicators)
    }

    @Test
    fun `a template with no id is refused, because nothing could ever apply it`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())

        store.save(momentum().copy(id = " "))

        assertEquals(emptyList<IndicatorTemplate>(), store.templates().first())
    }

    @Test
    fun `renaming leaves the creation time alone so the list does not reshuffle`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())
        store.save(momentum(createdAt = 99L))

        store.rename("tpl-1", "مومنتوم روزانه")

        val stored = store.get("tpl-1")!!
        assertEquals("مومنتوم روزانه", stored.name)
        assertEquals(99L, stored.createdAt)
        assertEquals(listOf("ema", "rsi", "macd"), stored.indicators)
    }

    @Test
    fun `deleting one template leaves every other one alone`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())
        store.save(momentum(id = "a", createdAt = 1L))
        store.save(momentum(id = "b", createdAt = 2L))

        store.delete("a")

        assertNull(store.get("a"))
        assertEquals(listOf("b"), store.templates().first().map(IndicatorTemplate::id))
    }

    @Test
    fun `deleting the last template removes the entry rather than leaving an empty string`() = runTest {
        val backing = FakeIndicatorTemplatePreferences()
        val store = IndicatorTemplateStore(backing)
        store.save(momentum())

        store.delete("tpl-1")

        assertNull(backing.data.first()[IndicatorTemplateStore.TEMPLATES])
    }

    @Test
    fun `templates come back newest first`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())
        store.save(momentum(id = "old", createdAt = 100L))
        store.save(momentum(id = "new", createdAt = 300L))
        store.save(momentum(id = "middle", createdAt = 200L))

        assertEquals(
            listOf("new", "middle", "old"),
            store.templates().first().map(IndicatorTemplate::id),
        )
    }

    @Test
    fun `a short row written by an older build decodes with defaults instead of being discarded`() {
        // Two fields where this version writes six: an id and a name, and nothing else. This is
        // what a template saved before periods and sources existed looks like on disk.
        val decoded = IndicatorTemplateStore.decode("tpl-1\u001Eمومنتوم")

        assertEquals("tpl-1", decoded?.id)
        assertEquals("مومنتوم", decoded?.name)
        assertEquals(emptyList<String>(), decoded?.indicators)
        assertEquals(emptyMap<String, Int>(), decoded?.periods)
        assertEquals(emptyMap<String, String>(), decoded?.sources)
        assertEquals(0L, decoded?.createdAt)
    }

    @Test
    fun `a row with a trailing field this build has never heard of decodes rather than failing`() {
        val record = IndicatorTemplateStore.encode(momentum())!! + "\u001Ea_field_from_a_newer_build"

        assertEquals(momentum(), IndicatorTemplateStore.decode(record))
    }

    @Test
    fun `a row with no id is dropped, because nothing can address it`() {
        assertNull(IndicatorTemplateStore.decode("\u001Eمومنتوم\u001Eema"))
    }

    @Test
    fun `a period with no number drops that entry and keeps the indicators around it`() = runTest {
        // A half-written map: three parts where pairs are expected.
        val record = listOf("tpl-1", "n", "ema\u001Frsi", "ema\u001F21\u001Frsi", "", "7")
            .joinToString("\u001E")
        val backing = FakeIndicatorTemplatePreferences(
            mutablePreferencesOf(IndicatorTemplateStore.TEMPLATES to record),
        )

        val stored = IndicatorTemplateStore(backing).get("tpl-1")!!

        assertEquals(listOf("ema", "rsi"), stored.indicators)
        assertEquals(mapOf("ema" to 21), stored.periods)
        assertEquals(7L, stored.createdAt)
    }

    @Test
    fun `an indicator id carrying a separator is dropped without taking the template with it`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())

        store.save(momentum().copy(indicators = listOf("ema", "bad\u001Eid")))

        val stored = store.get("tpl-1")!!
        assertEquals(listOf("ema"), stored.indicators)
        assertEquals("مومنتوم", stored.name)
    }

    @Test
    fun `a name carrying a separator is stripped rather than losing the template`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())

        store.save(momentum().copy(name = "مو\u001Eمنتوم"))

        assertEquals("مومنتوم", store.get("tpl-1")?.name)
    }

    @Test
    fun `the sanity cap holds and drops the oldest, not the newest`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())
        // Written oldest first, so the eviction cannot accidentally be "drop whatever came last".
        (1..IndicatorTemplateStore.MAX_TEMPLATES).forEach { index ->
            store.save(momentum(id = "tpl-$index", createdAt = index.toLong()))
        }

        store.save(momentum(id = "newest", createdAt = 10_000L))

        val ids = store.templates().first().map(IndicatorTemplate::id)
        assertEquals(IndicatorTemplateStore.MAX_TEMPLATES, ids.size)
        assertEquals("newest", ids.first())
        assertTrue("tpl-1" !in ids)
        assertTrue("tpl-2" in ids)
    }

    @Test
    fun `an id nobody saved reads back as null rather than as an empty template`() = runTest {
        val store = IndicatorTemplateStore(FakeIndicatorTemplatePreferences())
        store.save(momentum())

        assertNull(store.get("nothing"))
    }
}

private class FakeIndicatorTemplatePreferences(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
