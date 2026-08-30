package com.coinepro.feature.chart

import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProWindowClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pane cap and the grid arithmetic that gives it its meaning.
 *
 * The cap used to be a constant and could not be wrong; it is now a function of the window, and
 * every way it can be wrong is a way a reader loses something. Two on a tablet is the feature not
 * arriving. Eight on a phone is eight charts a hundred points tall, which is the exact failure the
 * cap was written to prevent — and eight live controllers on a phone besides. So both numbers are
 * pinned, and so is the arithmetic that decides how the panes are actually laid out, because a cap
 * of eight is only honest if eight panes really do fit somewhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartPaneCapTest {

    @Test
    fun `a phone keeps the two-pane cap it has always had`() {
        assertEquals(
            CoineProWindowClass.PHONE_MAX_PANES,
            maxPanesFor(CoineProWindowClass.of(411, 914)),
        )
        // Landscape on the same phone is still a phone: 914 across is wide, and it is exactly the
        // case the screen's own KDoc says gets two wide short panes rather than more of them.
        assertEquals(2, maxPanesFor(CoineProWindowClass.of(411, 914)))
    }

    @Test
    fun `a tablet gets eight`() {
        assertEquals(
            CoineProWindowClass.TABLET_MAX_PANES,
            maxPanesFor(CoineProWindowClass.of(1280, 800)),
        )
        assertEquals(8, maxPanesFor(CoineProWindowClass.of(800, 1280)))
    }

    @Test
    fun `a tablet in a narrow multi-window split is capped like the phone it is shaped like`() {
        // Not a device check anywhere in this path: a 500dp window is a 500dp window, and eight
        // panes in it would be the phone failure on a machine that could have done better in a
        // bigger split.
        assertEquals(2, maxPanesFor(CoineProWindowClass.of(500, 1280)))
    }

    @Test
    fun `a phone lays the panes out in one column, which is the layout it always had`() {
        assertEquals(1, paneColumns(width = 411.dp, count = 2))
        assertEquals(1, paneColumns(width = 411.dp, count = 8))
    }

    @Test
    fun `a landscape tablet gives eight panes three columns and therefore three rows`() {
        val columns = paneColumns(width = 1280.dp, count = 8)
        assertEquals(3, columns)
        val rows = (8 + columns - 1) / columns
        assertEquals(3, rows)
    }

    @Test
    fun `the grid never opens more columns than there are panes`() {
        assertEquals(2, paneColumns(width = 1280.dp, count = 2))
        assertEquals(1, paneColumns(width = 1280.dp, count = 1))
    }

    @Test
    fun `no pane is ever narrower than the floor the cap is argued from`() {
        // The whole "eight is fine on a tablet" argument rests on a pane still being 400dp across.
        // If the column count ever outruns that, the cap is a promise the layout does not keep.
        for (width in listOf(411, 600, 840, 1024, 1280, 1600)) {
            for (count in 2..CoineProWindowClass.TABLET_MAX_PANES) {
                val columns = paneColumns(width = width.dp, count = count)
                val perPane = width.dp / columns
                assertTrue(
                    "$count panes across ${width}dp gave $columns columns of $perPane",
                    columns == 1 || perPane >= 400.dp,
                )
            }
        }
    }

    @Test
    fun `the panes after the first come back in the order they were left in`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        store.setExtraPaneSymbols(listOf("xauusd", " ethusdt ", "BTCUSDT"))
        assertEquals(listOf("XAUUSD", "ETHUSDT", "BTCUSDT"), store.extraPaneSymbols.first())
    }

    @Test
    fun `a reader upgrading from the two-pane build keeps the pane they had`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        // The old key, written by a build that only knew about a second pane.
        store.setSecondPaneSymbol("XAGUSD")
        assertEquals(listOf("XAGUSD"), store.extraPaneSymbols.first())
    }

    @Test
    fun `writing the panes keeps the old second-pane key in step`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        store.setExtraPaneSymbols(listOf("XAUUSD", "ETHUSDT"))
        // A downgrade, or anything else that still reads the old key, finds the right answer rather
        // than a symbol from three arrangements ago.
        assertEquals("XAUUSD", store.secondPaneSymbol.first())
    }

    @Test
    fun `a record longer than the cap is truncated rather than turned into live controllers`() =
        runTest {
            val store = ChartWorkspaceStore(FakeWorkspacePreferences())
            store.setExtraPaneSymbols(List(40) { index -> "SYM$index" })
            val stored = store.extraPaneSymbols.first()
            // Seven, because the first pane is not in this list.
            assertEquals(CoineProWindowClass.TABLET_MAX_PANES - 1, stored.size)
        }

    @Test
    fun `a reader who has never split gets two panes`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        assertEquals(CoineProWindowClass.PHONE_MAX_PANES, store.paneCount.first())
    }

    @Test
    fun `a stored count below two is refused, because two is what makes this screen exist`() =
        runTest {
            val store = ChartWorkspaceStore(FakeWorkspacePreferences())
            store.setPaneCount(1)
            assertEquals(CoineProWindowClass.PHONE_MAX_PANES, store.paneCount.first())
        }

    @Test
    fun `a count stored on a tablet survives being read, so a phone visit does not truncate it`() =
        runTest {
            // The screen clamps against its own window and never writes the clamped value back.
            // This is the store's half of that contract: it hands back what was stored.
            val store = ChartWorkspaceStore(FakeWorkspacePreferences())
            store.setPaneCount(6)
            assertEquals(6, store.paneCount.first())
        }
}
