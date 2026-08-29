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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartLayoutStoreTest {

    /** ASCII record separator, the one the store puts between a record's fields. */
    private val recordSeparator = "\u001E"

    /** ASCII unit separator, the one it puts inside a list. */
    private val unitSeparator = "\u001F"

    private fun layout(
        id: String = "layout-1",
        name: String = "روند",
        updatedAt: Long = 1_700_000_000_000L,
    ) = ChartLayout(
        id = id,
        name = name,
        symbol = "XAUUSD",
        timeframe = "H4",
        chartType = "CANDLES",
        indicators = listOf("ema", "rsi"),
        indicatorPeriods = mapOf("ema" to 21, "rsi" to 14),
        scaleMode = "LOGARITHMIC",
        colourTemplate = ChartColourTemplate.BUILT_IN_DARK_ID,
        createdAt = 1_600_000_000_000L,
        updatedAt = updatedAt,
    )

    @Test
    fun `a layout round-trips with every field intact`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout())

        assertEquals(layout(), store.layouts().first().single())
    }

    @Test
    fun `a layout with no indicators round-trips as empty collections, not as ones holding a blank`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout().copy(indicators = emptyList(), indicatorPeriods = emptyMap(), colourTemplate = null))

        val stored = store.layouts().first().single()
        assertEquals(emptyList<String>(), stored.indicators)
        assertEquals(emptyMap<String, Int>(), stored.indicatorPeriods)
        assertNull(stored.colourTemplate)
    }

    @Test
    fun `layouts are listed newest first, so the one last worked in is at the top`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout(id = "a", name = "الف", updatedAt = 100L))
        store.save(layout(id = "b", name = "ب", updatedAt = 300L))
        store.save(layout(id = "c", name = "پ", updatedAt = 200L))

        assertEquals(listOf("b", "c", "a"), store.layouts().first().map(ChartLayout::id))
    }

    @Test
    fun `saving under an existing id replaces that layout rather than duplicating it`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout(updatedAt = 1L))
        store.save(layout(updatedAt = 2L).copy(timeframe = "D1"))

        assertEquals(1, store.layouts().first().size)
        assertEquals("D1", store.layouts().first().single().timeframe)
    }

    @Test
    fun `renaming keeps the id and everything stored under it`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout())

        store.rename("layout-1", "روند بلندمدت")

        assertEquals(layout().copy(name = "روند بلندمدت"), store.layouts().first().single())
    }

    @Test
    fun `renaming an id nobody has stored changes nothing`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout())

        store.rename("not-a-layout", "چیز دیگر")

        assertEquals(layout(), store.layouts().first().single())
    }

    @Test
    fun `a name carrying a separator is refused rather than written back wrong`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())

        store.save(layout(name = "a" + recordSeparator + "b"))

        // Refused, not sanitised. Silently renaming somebody's layout is worse than not saving it,
        // and writing it would produce a record that parses back as different fields.
        assertEquals(emptyList<ChartLayout>(), store.layouts().first())
    }

    @Test
    fun `get returns one layout by id and null for anything else`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout(id = "a", updatedAt = 1L))
        store.save(layout(id = "b", updatedAt = 2L))

        assertEquals("a", store.get("a")?.id)
        assertNull(store.get("c"))
    }

    @Test
    fun `deleting removes only the named layout`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout(id = "a", updatedAt = 1L))
        store.save(layout(id = "b", updatedAt = 2L))

        store.delete("a")

        assertEquals(listOf("b"), store.layouts().first().map(ChartLayout::id))
    }

    @Test
    fun `deleting the layout the chart would restore also clears the pointer to it`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout(id = "a", updatedAt = 1L))
        store.setLastOpened("a")

        store.delete("a")

        // A dangling id would send the next chart open looking for a layout that is not there.
        assertNull(store.lastOpened().first())
    }

    @Test
    fun `deleting a layout leaves a pointer at a different layout alone`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        store.save(layout(id = "a", updatedAt = 1L))
        store.save(layout(id = "b", updatedAt = 2L))
        store.setLastOpened("b")

        store.delete("a")

        assertEquals("b", store.lastOpened().first())
    }

    @Test
    fun `the last opened layout is remembered and can be cleared`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())

        assertNull(store.lastOpened().first())
        store.setLastOpened("layout-1")
        assertEquals("layout-1", store.lastOpened().first())
        store.setLastOpened(null)
        assertNull(store.lastOpened().first())
    }

    @Test
    fun `the two built-in colour templates are there before anybody has saved one`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())

        val templates = store.templates().first()
        assertEquals(
            listOf(ChartColourTemplate.BUILT_IN_DARK_ID, ChartColourTemplate.BUILT_IN_LIGHT_ID),
            templates.map(ChartColourTemplate::id),
        )
        // The project's own market palette rather than an invented pair: these are
        // CoineProDarkPalette's buy and sell.
        assertEquals(0xFF00B15C, templates.first().up)
        assertEquals(0xFFF6465D, templates.first().down)
        assertTrue(templates.all(ChartColourTemplate::isBuiltIn))
    }

    @Test
    fun `a built-in colour template cannot be deleted`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())

        store.deleteTemplate(ChartColourTemplate.BUILT_IN_DARK_ID)
        store.deleteTemplate(ChartColourTemplate.BUILT_IN_LIGHT_ID)

        assertEquals(ChartColourTemplate.BUILT_IN, store.templates().first())
    }

    @Test
    fun `a template saved under a built-in id is refused, so the default can never be shadowed`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())

        store.saveTemplate(ChartColourTemplate.Dark.copy(up = 0xFF000000))

        assertEquals(0xFF00B15C, store.templates().first().first().up)
        assertEquals(2, store.templates().first().size)
    }

    @Test
    fun `a reader's own colour template round-trips, lists after the built-ins and can be deleted`() = runTest {
        val store = ChartLayoutStore(FakeLayoutPreferences())
        val mine = ChartColourTemplate(
            id = "mine",
            name = "شب",
            up = 0xFF2ECC71,
            down = 0xFFE74C3C,
            grid = 0xFF202020,
            background = 0xFF000000,
            text = 0xFFEEEEEE,
            crosshair = 0xFF9E9E9E,
        )

        store.saveTemplate(mine)

        assertEquals(3, store.templates().first().size)
        assertEquals(mine, store.templates().first().last())

        store.deleteTemplate("mine")
        assertEquals(ChartColourTemplate.BUILT_IN, store.templates().first())
    }

    @Test
    fun `a short layout row written by an older build decodes with defaults instead of being discarded`() {
        // Three fields where this version writes eleven.
        val decoded = ChartLayoutStore.decodeLayout(
            listOf("layout-1", "روند", "XAUUSD").joinToString(recordSeparator),
        )

        assertEquals("layout-1", decoded?.id)
        assertEquals("روند", decoded?.name)
        assertEquals("XAUUSD", decoded?.symbol)
        assertEquals("", decoded?.timeframe)
        assertEquals(emptyList<String>(), decoded?.indicators)
        assertEquals(emptyMap<String, Int>(), decoded?.indicatorPeriods)
        assertNull(decoded?.colourTemplate)
        assertEquals(0L, decoded?.updatedAt)
    }

    @Test
    fun `a layout row with a trailing field this build has never heard of decodes rather than failing`() {
        val record = ChartLayoutStore.encodeLayout(layout())!! +
            recordSeparator + "a_field_from_a_newer_build"

        assertEquals(layout(), ChartLayoutStore.decodeLayout(record))
    }

    @Test
    fun `a layout row with no id is dropped, because nothing can address it`() {
        assertNull(ChartLayoutStore.decodeLayout(listOf("", "روند", "XAUUSD").joinToString(recordSeparator)))
    }

    @Test
    fun `a short colour template row falls back to the dark built-in for the colours it is missing`() {
        val decoded = ChartLayoutStore.decodeTemplate(
            listOf("mine", "شب", ChartColourTemplate.Light.up.toString()).joinToString(recordSeparator),
        )

        assertNotNull(decoded)
        assertEquals("mine", decoded?.id)
        assertEquals(ChartColourTemplate.Light.up, decoded?.up)
        assertEquals(ChartColourTemplate.Dark.down, decoded?.down)
        assertEquals(ChartColourTemplate.Dark.crosshair, decoded?.crosshair)
    }

    @Test
    fun `records written under the old chart_layouts key are left alone rather than read wrong`() = runTest {
        // The first version of this store wrote four differently ordered fields under
        // "chart_layouts". Decoding those here would produce a layout whose timeframe is an
        // indicator list, so the new key ignores them entirely.
        val old = listOf("روند", "candles", "H1", "ema20").joinToString(unitSeparator)
        val backing = FakeLayoutPreferences(
            mutablePreferencesOf(stringPreferencesKey("chart_layouts") to old),
        )

        assertEquals(emptyList<ChartLayout>(), ChartLayoutStore(backing).layouts().first())
    }
}

private class FakeLayoutPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
