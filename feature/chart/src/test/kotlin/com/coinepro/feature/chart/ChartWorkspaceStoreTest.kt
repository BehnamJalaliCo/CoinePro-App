package com.coinepro.feature.chart

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the chart workspace remembers between visits.
 *
 * This file used to be `ChartSplitTest` and most of it was about the divider between the chart and
 * the watchlist — the bounds, the clamp, the drag arithmetic, the stored ratio. There is no divider
 * now; the chart page has the whole screen. See `ChartScreen` for why. What is left is the part of
 * the store the panes screen still uses, and [FakeWorkspacePreferences], which every test of these
 * stores is built on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartWorkspaceStoreTest {

    @Test
    fun `the second pane's symbol is stored uppercased so two spellings are one instrument`() =
        runTest {
            val store = ChartWorkspaceStore(FakeWorkspacePreferences())
            store.setSecondPaneSymbol(" btcusdt ")
            assertEquals("BTCUSDT", store.secondPaneSymbol.first())
        }

    @Test
    fun `a blank second-pane symbol is refused rather than stored as an empty row`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        store.setSecondPaneSymbol("XAUUSD")
        store.setSecondPaneSymbol("   ")
        assertEquals("XAUUSD", store.secondPaneSymbol.first())
    }
}

/** The preferences file as an in-memory value, which is all these stores need to be exercised. */
internal class FakeWorkspacePreferences(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
