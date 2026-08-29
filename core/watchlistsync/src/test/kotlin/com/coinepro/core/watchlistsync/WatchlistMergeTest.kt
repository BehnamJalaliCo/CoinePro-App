package com.coinepro.core.watchlistsync

import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistColumn
import com.coinepro.core.datastore.WatchlistFlag
import com.coinepro.core.datastore.WatchlistSettings
import com.coinepro.core.datastore.WatchlistSnapshot
import com.coinepro.core.datastore.WatchlistSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides what a reader keeps when two of their phones disagree.
 *
 * The case these tests exist for is the one the feature was asked for: somebody builds a list on a
 * second device, opens the first, and finds out whether this app is one of the ones that throws it
 * away.
 */
class WatchlistMergeTest {

    private fun list(
        id: String,
        vararg symbols: String,
        name: String = id,
        createdAt: Long = 1_000L,
        updatedAt: Long = 1_000L,
    ) = Watchlist(id, name, symbols.toList(), createdAt, updatedAt)

    private fun snapshot(
        vararg lists: Watchlist,
        settings: Map<String, WatchlistSettings> = emptyMap(),
        tombstones: Map<String, Long> = emptyMap(),
    ) = WatchlistSnapshot(lists.toList(), settings, tombstones)

    @Test
    fun `a list made on the other phone survives a sync from this one`() {
        val local = snapshot(list("default", "BTCUSDT"))
        val remote = snapshot(list("default", "BTCUSDT"), list("list_b", "SOLUSDT", createdAt = 2_000L))

        val result = WatchlistMerge.merge(local, remote)

        assertEquals(listOf("default", "list_b"), result.snapshot.lists.map { it.id })
        assertEquals(1, result.listsAdopted)
        assertEquals(1, result.symbolsAdopted)
    }

    @Test
    fun `a list made on this phone is not lost to a server that has never heard of it`() {
        val local = snapshot(list("default"), list("list_a", "ETHUSDT", createdAt = 2_000L))
        val remote = snapshot(list("default"))

        val result = WatchlistMerge.merge(local, remote)

        assertEquals(listOf("default", "list_a"), result.snapshot.lists.map { it.id })
        // Nothing came in, so the reader is told nothing arrived rather than being given a count
        // for their own data being sent out.
        assertEquals(0, result.listsAdopted)
        assertEquals(0, result.symbolsAdopted)
    }

    @Test
    fun `both sides added symbols to the same list and both sets survive`() {
        val local = snapshot(list("default", "BTCUSDT", "ETHUSDT", updatedAt = 5_000L))
        val remote = snapshot(list("default", "BTCUSDT", "SOLUSDT", "XRPUSDT", updatedAt = 4_000L))

        val result = WatchlistMerge.merge(local, remote)

        // The newer side supplies the order and the older side's extras are appended, never
        // interleaved: the reader's dragged arrangement is not rewritten by a merge.
        assertEquals(
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT"),
            result.snapshot.lists.single().symbols,
        )
        assertEquals(2, result.symbolsAdopted)
    }

    @Test
    fun `the more recently touched side supplies the order`() {
        val local = snapshot(list("default", "BTCUSDT", "ETHUSDT", updatedAt = 1_000L))
        val remote = snapshot(list("default", "SOLUSDT", "BTCUSDT", updatedAt = 9_000L))

        val result = WatchlistMerge.merge(local, remote)

        assertEquals(listOf("SOLUSDT", "BTCUSDT", "ETHUSDT"), result.snapshot.lists.single().symbols)
    }

    @Test
    fun `a name only ever comes from the side that touched the list last`() {
        val local = snapshot(list("list_a", name = "قدیمی", updatedAt = 1_000L))
        val remote = snapshot(list("list_a", name = "تازه", updatedAt = 2_000L))

        assertEquals("تازه", WatchlistMerge.merge(local, remote).snapshot.lists.single().name)
        // Both ways round, because the rule is a clock and not a preference for whichever side the
        // document happened to arrive from.
        assertEquals("تازه", WatchlistMerge.merge(remote, local).snapshot.lists.single().name)
    }

    @Test
    fun `a tie in the clock goes to this device`() {
        val local = snapshot(list("list_a", name = "مال من", updatedAt = 7_000L))
        val remote = snapshot(list("list_a", name = "مال او", updatedAt = 7_000L))

        assertEquals("مال من", WatchlistMerge.merge(local, remote).snapshot.lists.single().name)
    }

    @Test
    fun `a list deleted here is deleted there, and stays deleted`() {
        val local = snapshot(list("default"), tombstones = mapOf("list_a" to 5_000L))
        val remote = snapshot(list("default"), list("list_a", "SOLUSDT", updatedAt = 4_000L))

        val result = WatchlistMerge.merge(local, remote)

        assertEquals(listOf("default"), result.snapshot.lists.map { it.id })
        assertEquals(0, result.listsDropped)
        // The tombstone travels, or a third device would put the list back on its next sync.
        assertEquals(mapOf("list_a" to 5_000L), result.snapshot.tombstones)
    }

    @Test
    fun `a list deleted on the other phone disappears here and the reader is told`() {
        val local = snapshot(list("default"), list("list_a", "SOLUSDT", updatedAt = 4_000L))
        val remote = snapshot(list("default"), tombstones = mapOf("list_a" to 5_000L))

        val result = WatchlistMerge.merge(local, remote)

        assertEquals(listOf("default"), result.snapshot.lists.map { it.id })
        assertEquals(1, result.listsDropped)
    }

    @Test
    fun `editing a list after deleting it brings it back`() {
        val local = snapshot(list("default"), tombstones = mapOf("list_a" to 5_000L))
        val remote = snapshot(list("default"), list("list_a", "SOLUSDT", updatedAt = 6_000L))

        val result = WatchlistMerge.merge(local, remote)

        assertEquals(listOf("default", "list_a"), result.snapshot.lists.map { it.id })
        // The tombstone is discarded with it, or the next sync would delete the list again.
        assertTrue(result.snapshot.tombstones.isEmpty())
    }

    @Test
    fun `a tombstone naming the default list is refused`() {
        // Nothing can write one — the store refuses to delete that list — so this can only be a
        // corrupted document. Honouring it would leave every watchlist-scoped alert resolving to
        // nothing, firing never, and looking exactly like an alert that works.
        val local = snapshot(list("default", "BTCUSDT"))
        val remote = snapshot(tombstones = mapOf("default" to 9_999L))

        val result = WatchlistMerge.merge(local, remote)

        assertEquals(listOf("default"), result.snapshot.lists.map { it.id })
        assertEquals(listOf("BTCUSDT"), result.snapshot.lists.single().symbols)
    }

    @Test
    fun `a tombstone neither side still has a list for keeps travelling`() {
        val local = snapshot(list("default"), tombstones = mapOf("list_a" to 5_000L))
        val remote = snapshot(list("default"))

        assertEquals(mapOf("list_a" to 5_000L), WatchlistMerge.merge(local, remote).snapshot.tombstones)
    }

    @Test
    fun `a first-ever sync keeps everything this device has`() {
        // What the server answers a reader who has never synced with: version zero, empty payload.
        val local = snapshot(
            list("default", "BTCUSDT", "ETHUSDT"),
            list("list_a", "SOLUSDT", createdAt = 2_000L),
            settings = mapOf("list_a" to WatchlistSettings(flags = mapOf("SOLUSDT" to WatchlistFlag.RED))),
        )

        val result = WatchlistMerge.merge(local, WatchlistPayload.decode(null))

        assertEquals(local.lists, result.snapshot.lists)
        assertEquals(local.settings, result.snapshot.settings)
        assertTrue(!result.changed)
    }

    @Test
    fun `an empty remote takes nothing away`() {
        val local = snapshot(list("default", "BTCUSDT"), list("list_a", "SOLUSDT", createdAt = 2_000L))

        val result = WatchlistMerge.merge(local, WatchlistSnapshot())

        assertEquals(listOf("default", "list_a"), result.snapshot.lists.map { it.id })
        assertEquals(0, result.listsDropped)
    }

    @Test
    fun `flags are merged per symbol and only a disagreement costs a colour`() {
        val local = snapshot(
            list("list_a", "BTCUSDT", "ETHUSDT", updatedAt = 9_000L),
            settings = mapOf(
                "list_a" to WatchlistSettings(
                    flags = mapOf("BTCUSDT" to WatchlistFlag.RED),
                    columns = setOf(WatchlistColumn.LAST_PRICE),
                    sort = WatchlistSort(WatchlistColumn.CHANGE_PERCENT, descending = false),
                ),
            ),
        )
        val remote = snapshot(
            list("list_a", "BTCUSDT", "ETHUSDT", updatedAt = 1_000L),
            settings = mapOf(
                "list_a" to WatchlistSettings(
                    flags = mapOf("BTCUSDT" to WatchlistFlag.BLUE, "ETHUSDT" to WatchlistFlag.GREEN),
                    columns = setOf(WatchlistColumn.VOLUME),
                ),
            ),
        )

        val merged = WatchlistMerge.merge(local, remote).snapshot.settings.getValue("list_a")

        assertEquals(WatchlistFlag.RED, merged.flags["BTCUSDT"])
        assertEquals(WatchlistFlag.GREEN, merged.flags["ETHUSDT"])
        assertEquals(setOf(WatchlistColumn.LAST_PRICE), merged.columns)
        assertEquals(WatchlistSort(WatchlistColumn.CHANGE_PERCENT, descending = false), merged.sort)
    }

    @Test
    fun `settings of a list the merge dropped are dropped with it`() {
        val local = snapshot(
            list("default"),
            list("list_a", "SOLUSDT", updatedAt = 1_000L),
            settings = mapOf("list_a" to WatchlistSettings(flags = mapOf("SOLUSDT" to WatchlistFlag.RED))),
        )
        val remote = snapshot(list("default"), tombstones = mapOf("list_a" to 5_000L))

        assertNull(WatchlistMerge.merge(local, remote).snapshot.settings["list_a"])
    }

    @Test
    fun `the default list is first and the rest follow the order they were made`() {
        val local = snapshot(list("list_c", createdAt = 3_000L), list("default", createdAt = 1L))
        val remote = snapshot(list("list_b", createdAt = 2_000L))

        val result = WatchlistMerge.merge(local, remote)

        assertEquals(listOf("default", "list_b", "list_c"), result.snapshot.lists.map { it.id })
    }

    @Test
    fun `a list the two devices disagree about the age of takes the earlier birthday`() {
        val local = snapshot(list("list_a", createdAt = 0L, updatedAt = 9_000L))
        val remote = snapshot(list("list_a", createdAt = 2_000L, updatedAt = 1_000L))

        // Zero is what a record written before this store kept creation times reads back as, and
        // taking it would date the list to the epoch and pin it to the front of the switcher.
        assertEquals(2_000L, WatchlistMerge.merge(local, remote).snapshot.lists.single().createdAt)
    }
}
