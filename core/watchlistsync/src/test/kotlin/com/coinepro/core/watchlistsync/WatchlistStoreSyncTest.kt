package com.coinepro.core.watchlistsync

import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistColumn
import com.coinepro.core.datastore.WatchlistFlag
import com.coinepro.core.datastore.WatchlistSettings
import com.coinepro.core.datastore.WatchlistSnapshot
import com.coinepro.core.datastore.WatchlistSort
import com.coinepro.core.datastore.WatchlistStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three things `WatchlistStore` grew for this module, tested from the module that needs them.
 *
 * They live in `core:datastore` because they have to be inside its one preferences edit to be
 * correct, and they are exercised here because here is where their being wrong would show: a
 * tombstone that is not written is a delete that never propagates, and an `updatedAt` that does
 * not move is a flag the merge throws away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistStoreSyncTest {

    private fun store(clock: () -> Long = { 1_000L }) = WatchlistStore(FakeDataStore(), now = clock)

    @Test
    fun `deleting a list records when it was deleted`() = runTest {
        var clock = 1_000L
        val store = store { clock }
        val id = store.create("موقت")
        clock = 7_000L
        store.delete(id)

        val snapshot = store.snapshot()

        assertTrue(snapshot.lists.none { it.id == id })
        assertEquals(mapOf(id to 7_000L), snapshot.tombstones)
    }

    @Test
    fun `a tombstone is invisible to everything except a snapshot`() = runTest {
        val store = store()
        val id = store.create("موقت")
        store.delete(id)

        // No screen may ever see one. `lists()` is what the switcher draws.
        assertEquals(listOf(Watchlist.DEFAULT_LIST_ID), store.lists().first().map { it.id })
    }

    @Test
    fun `a deletion older than the window stops being carried`() = runTest {
        var clock = 1_000L
        val store = store { clock }
        val old = store.create("قدیمی")
        store.delete(old)
        // Ninety days and a second later, and one more deletion to trigger the prune.
        clock = 1_000L + WatchlistStore.TOMBSTONE_TTL_MS + 1_000L
        val fresh = store.create("تازه")
        store.delete(fresh)

        assertEquals(setOf(fresh), store.snapshot().tombstones.keys)
    }

    @Test
    fun `colouring a symbol moves the list's clock`() = runTest {
        var clock = 1_000L
        val store = store { clock }
        store.toggle("BTCUSDT")
        val before = store.lists().first().single().updatedAt
        clock = 5_000L
        store.flag(Watchlist.DEFAULT_LIST_ID, "BTCUSDT", WatchlistFlag.RED)

        // Without this the merge settles flags with a clock that never moved, and six recolourings
        // lose to a phone that was merely opened more recently.
        assertNotEquals(before, store.lists().first().single().updatedAt)
        assertEquals(5_000L, store.lists().first().single().updatedAt)
    }

    @Test
    fun `choosing columns and a sort moves it too`() = runTest {
        var clock = 1_000L
        val store = store { clock }
        clock = 6_000L
        store.setColumns(Watchlist.DEFAULT_LIST_ID, setOf(WatchlistColumn.LAST_PRICE))
        assertEquals(6_000L, store.lists().first().single().updatedAt)

        clock = 8_000L
        store.setSort(Watchlist.DEFAULT_LIST_ID, WatchlistSort(WatchlistColumn.VOLUME))
        assertEquals(8_000L, store.lists().first().single().updatedAt)
    }

    @Test
    fun `a merge written back takes the settings of dropped lists with it`() = runTest {
        val store = store()
        val id = store.create("موقت")
        store.add(id, "SOLUSDT")
        store.flag(id, "SOLUSDT", WatchlistFlag.RED)
        store.setActiveList(id)

        store.applyMerged { current ->
            current.copy(lists = current.lists.filterNot { it.id == id }, settings = emptyMap())
        }

        assertEquals(listOf(Watchlist.DEFAULT_LIST_ID), store.lists().first().map { it.id })
        // Left behind, this would repaint the reader's flag onto whatever list next took the id.
        assertEquals(WatchlistSettings(), store.settings(id).first())
        // And the pointer cannot be left naming a list that is gone.
        assertEquals(Watchlist.DEFAULT_LIST_ID, store.activeListId().first())
    }

    @Test
    fun `a merge runs against what is stored at the moment of the write`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")

        // The transform is handed the current snapshot rather than one captured before a network
        // round trip, which is what stops a star placed during the sync being overwritten.
        var seen: WatchlistSnapshot? = null
        store.applyMerged { current -> seen = current; current }

        assertEquals(listOf("BTCUSDT"), seen?.lists?.first()?.symbols)
    }

    @Test
    fun `the cursor is only what a write actually earned`() = runTest {
        val store = store()

        assertEquals(0L, store.syncCursor().first().version)
        store.recordSynced(version = 12L, syncedAtMs = 99L)
        assertEquals(12L, store.syncCursor().first().version)
        assertEquals(99L, store.syncCursor().first().syncedAtMs)
    }

    @Test
    fun `a list still on the defaults contributes no settings to a snapshot`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")

        // Otherwise "the reader chose exactly the default three columns" and "nobody has been in
        // here" are the same document, and fifty lists of nothing fill the 64 KB budget.
        assertTrue(store.snapshot().settings.isEmpty())
    }
}
