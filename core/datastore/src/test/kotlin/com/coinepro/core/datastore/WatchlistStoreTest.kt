package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.coinepro.core.notifications.AlertScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The property that makes a watchlist a watchlist: the order is the reader's, and nothing the
 * market does may rearrange it — and, since this store grew named lists, that nothing an *update*
 * does may lose it either.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistStoreTest {

    @Test
    fun `symbols keep the order they were added in`() = runTest {
        val store = WatchlistStore(FakeDataStore())

        store.toggle("SOLUSDT")
        store.toggle("BTCUSDT")
        store.toggle("ETHUSDT")

        // Not alphabetical, not by price, not by anything the feed decides.
        assertEquals(listOf("SOLUSDT", "BTCUSDT", "ETHUSDT"), store.symbols.first())
    }

    @Test
    fun `toggling something already there removes it`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        store.toggle("BTCUSDT")
        store.toggle("ETHUSDT")

        store.toggle("BTCUSDT")

        assertEquals(listOf("ETHUSDT"), store.symbols.first())
    }

    @Test
    fun `a ticker is normalised, so one symbol cannot appear twice in two cases`() = runTest {
        val store = WatchlistStore(FakeDataStore())

        store.toggle("btcusdt")
        store.toggle(" BTCUSDT ")

        // The second call is a removal, not a duplicate. A star that showed "on" and added a second
        // row would look like a bug in the feed rather than in the store.
        assertEquals(emptyList<String>(), store.symbols.first())
    }

    @Test
    fun `an empty store is an empty list, not a list holding one blank`() = runTest {
        assertEquals(emptyList<String>(), WatchlistStore(FakeDataStore()).symbols.first())
    }

    @Test
    fun `a store that has never been written opens on the starter list`() = runTest {
        val starter = listOf("BTCUSDT", "ETHUSDT", "XAUUSD")
        val store = WatchlistStore(FakeDataStore(), starter = starter)

        assertEquals(starter, store.symbols.first())
        assertEquals(starter, store.lists().first().single().symbols)
    }

    @Test
    fun `the starter is a seed, not a floor - an emptied list stays empty`() = runTest {
        val store = WatchlistStore(FakeDataStore(), starter = listOf("BTCUSDT"))

        store.toggle("BTCUSDT")

        assertEquals(emptyList<String>(), store.symbols.first())
    }

    @Test
    fun `a legacy list wins over the starter`() = runTest {
        val store = WatchlistStore(
            FakeDataStore(mutablePreferencesOf(WatchlistStore.LEGACY_SYMBOLS to "SOLUSDT")),
            starter = listOf("BTCUSDT"),
        )

        assertEquals(listOf("SOLUSDT"), store.symbols.first())
    }

    @Test
    fun `a store that has never been written still offers the default list`() = runTest {
        val lists = WatchlistStore(FakeDataStore()).lists().first()

        assertEquals(1, lists.size)
        assertEquals(Watchlist.DEFAULT_LIST_ID, lists.single().id)
        assertEquals(Watchlist.DEFAULT_LIST_NAME, lists.single().name)
    }

    @Test
    fun `the single list an older build wrote becomes the default list`() = runTest {
        // Exactly what the previous version of this file put on disk: one key, tickers joined by a
        // vertical bar, and no version marker anywhere to consult.
        val store = WatchlistStore(
            FakeDataStore(mutablePreferencesOf(WatchlistStore.LEGACY_SYMBOLS to "BTCUSDT|ETHUSDT|SOLUSDT")),
        )

        val lists = store.lists().first()

        assertEquals(1, lists.size)
        assertEquals(Watchlist.DEFAULT_LIST_ID, lists.single().id)
        // In the order the reader put them in, not re-sorted on the way through.
        assertEquals(listOf("BTCUSDT", "ETHUSDT", "SOLUSDT"), lists.single().symbols)
    }

    @Test
    fun `lifted symbols survive the first write and the legacy key is retired`() = runTest {
        val backing = FakeDataStore(
            mutablePreferencesOf(WatchlistStore.LEGACY_SYMBOLS to "BTCUSDT|ETHUSDT"),
        )
        val store = WatchlistStore(backing)

        store.toggle("SOLUSDT")

        assertEquals(listOf("BTCUSDT", "ETHUSDT", "SOLUSDT"), store.symbols.first())
        // Retired rather than left lying about: an emptied default list must not refill itself
        // from a string the reader thought they had cleared.
        assertNull(backing.data.value[WatchlistStore.LEGACY_SYMBOLS])
    }

    @Test
    fun `emptying the default list after a migration leaves it empty`() = runTest {
        val store = WatchlistStore(
            FakeDataStore(mutablePreferencesOf(WatchlistStore.LEGACY_SYMBOLS to "BTCUSDT|ETHUSDT")),
        )

        store.clear()

        assertEquals(emptyList<String>(), store.symbols.first())
    }

    @Test
    fun `the default list cannot be deleted, only renamed and emptied`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")

        store.delete(Watchlist.DEFAULT_LIST_ID)

        // Still there, still holding what it held. An alert scoped to this id would otherwise stop
        // resolving while looking exactly like an alert that works.
        val lists = store.lists().first()
        assertEquals(listOf(Watchlist.DEFAULT_LIST_ID), lists.map(Watchlist::id))
        assertEquals(listOf("BTCUSDT"), lists.single().symbols)

        store.rename(Watchlist.DEFAULT_LIST_ID, "پرتفوی")
        assertEquals("پرتفوی", store.lists().first().single().name)
    }

    @Test
    fun `a list the reader made can be deleted, and takes its columns with it`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        val id = store.create("فلزات")
        store.setColumns(id, setOf(WatchlistColumn.LAST_PRICE, WatchlistColumn.VOLUME))

        store.delete(id)

        assertEquals(listOf(Watchlist.DEFAULT_LIST_ID), store.lists().first().map(Watchlist::id))
        assertEquals(WatchlistColumn.DEFAULT, store.settings(id).first().columns)
    }

    @Test
    fun `there is no cap a reader reaches on how many lists they may keep`() = runTest {
        val store = WatchlistStore(FakeDataStore())

        repeat(20) { index -> store.create("فهرست $index") }

        // Twenty is nineteen more than the free tier of the app this one is measured against.
        assertEquals(21, store.lists().first().size)
    }

    @Test
    fun `a new list is made with the name it was given and an id of its own`() = runTest {
        val store = WatchlistStore(FakeDataStore(), now = { 1_700_000_000_000L })

        val id = store.create("  ارزهای لایه دو  ")

        val made = store.lists().first().first { it.id == id }
        assertEquals("ارزهای لایه دو", made.name)
        assertEquals(1_700_000_000_000L, made.createdAt)
        assertTrue(id != Watchlist.DEFAULT_LIST_ID)
    }

    @Test
    fun `a blank name makes no list at all`() = runTest {
        val store = WatchlistStore(FakeDataStore())

        assertEquals("", store.create("   "))
        assertEquals(1, store.lists().first().size)
    }

    @Test
    fun `symbols live in one list at a time, so two lists do not share membership`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        val second = store.create("نامزدها")

        store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")
        store.add(second, "ETHUSDT")

        assertEquals(listOf("BTCUSDT"), store.symbols(Watchlist.DEFAULT_LIST_ID).first())
        assertEquals(listOf("ETHUSDT"), store.symbols(second).first())
    }

    @Test
    fun `move puts a dragged symbol where it was dropped`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        listOf("BTCUSDT", "ETHUSDT", "SOLUSDT").forEach { store.add(Watchlist.DEFAULT_LIST_ID, it) }

        store.move(Watchlist.DEFAULT_LIST_ID, from = 2, to = 0)

        assertEquals(
            listOf("SOLUSDT", "BTCUSDT", "ETHUSDT"),
            store.symbols(Watchlist.DEFAULT_LIST_ID).first(),
        )
    }

    @Test
    fun `a drag that ended outside the list changes nothing`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")

        store.move(Watchlist.DEFAULT_LIST_ID, from = 0, to = 7)

        assertEquals(listOf("BTCUSDT"), store.symbols(Watchlist.DEFAULT_LIST_ID).first())
    }

    @Test
    fun `a flag is set, read back, and cleared`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")

        store.flag(Watchlist.DEFAULT_LIST_ID, "btcusdt", WatchlistFlag.BLUE)
        assertEquals(
            mapOf("BTCUSDT" to WatchlistFlag.BLUE),
            store.settings(Watchlist.DEFAULT_LIST_ID).first().flags,
        )

        store.flag(Watchlist.DEFAULT_LIST_ID, "BTCUSDT", null)
        assertEquals(emptyMap<String, WatchlistFlag>(), store.settings(Watchlist.DEFAULT_LIST_ID).first().flags)
    }

    @Test
    fun `filtering by flag is what the colours are for`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        listOf("BTCUSDT", "ETHUSDT", "SOLUSDT").forEach { store.add(Watchlist.DEFAULT_LIST_ID, it) }
        store.flag(Watchlist.DEFAULT_LIST_ID, "BTCUSDT", WatchlistFlag.RED)
        store.flag(Watchlist.DEFAULT_LIST_ID, "SOLUSDT", WatchlistFlag.RED)
        store.flag(Watchlist.DEFAULT_LIST_ID, "ETHUSDT", WatchlistFlag.GREEN)

        assertEquals(
            listOf("BTCUSDT", "SOLUSDT"),
            store.symbols(Watchlist.DEFAULT_LIST_ID, WatchlistFlag.RED).first(),
        )
    }

    @Test
    fun `the same symbol carries a different flag in a different list`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        val second = store.create("نامزدها")
        store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")
        store.add(second, "BTCUSDT")

        store.flag(Watchlist.DEFAULT_LIST_ID, "BTCUSDT", WatchlistFlag.RED)
        store.flag(second, "BTCUSDT", WatchlistFlag.BLUE)

        assertEquals(WatchlistFlag.RED, store.settings(Watchlist.DEFAULT_LIST_ID).first().flags["BTCUSDT"])
        assertEquals(WatchlistFlag.BLUE, store.settings(second).first().flags["BTCUSDT"])
    }

    @Test
    fun `removing a symbol drops the flag that was on it`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")
        store.flag(Watchlist.DEFAULT_LIST_ID, "BTCUSDT", WatchlistFlag.PURPLE)

        store.remove(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")

        // Re-adding it must not bring back a colour the reader does not remember choosing.
        store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")
        assertNull(store.settings(Watchlist.DEFAULT_LIST_ID).first().flags["BTCUSDT"])
    }

    @Test
    fun `columns and sort are remembered per list, not shared between them`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        val second = store.create("نامزدها")

        store.setColumns(
            Watchlist.DEFAULT_LIST_ID,
            setOf(WatchlistColumn.FLAG, WatchlistColumn.LAST_PRICE, WatchlistColumn.QUOTE_VOLUME),
        )
        store.setSort(Watchlist.DEFAULT_LIST_ID, WatchlistSort(WatchlistColumn.QUOTE_VOLUME, descending = true))
        store.setColumns(second, setOf(WatchlistColumn.LAST_PRICE, WatchlistColumn.DAY_HIGH))
        store.setSort(second, WatchlistSort(WatchlistColumn.CHANGE_PERCENT, descending = false))

        val default = store.settings(Watchlist.DEFAULT_LIST_ID).first()
        assertEquals(
            setOf(WatchlistColumn.FLAG, WatchlistColumn.LAST_PRICE, WatchlistColumn.QUOTE_VOLUME),
            default.columns,
        )
        assertEquals(WatchlistSort(WatchlistColumn.QUOTE_VOLUME, descending = true), default.sort)

        val candidates = store.settings(second).first()
        assertEquals(setOf(WatchlistColumn.LAST_PRICE, WatchlistColumn.DAY_HIGH), candidates.columns)
        assertEquals(WatchlistSort(WatchlistColumn.CHANGE_PERCENT, descending = false), candidates.sort)
    }

    @Test
    fun `a list nobody has configured shows the columns that fit a narrow phone`() = runTest {
        val store = WatchlistStore(FakeDataStore())

        val settings = store.settings(Watchlist.DEFAULT_LIST_ID).first()

        // The flag rail, the day's line, the price and the change: 52 + 80 + 60 dp of figures and
        // the rest to the name, which on a 393 dp phone is still wider than «بیت‌کوین/تتر».
        assertEquals(
            setOf(WatchlistColumn.FLAG, WatchlistColumn.SPARKLINE, WatchlistColumn.LAST_PRICE, WatchlistColumn.CHANGE_PERCENT),
            settings.columns,
        )
        assertTrue(settings.sort.isManual)
    }

    @Test
    fun `unticking the last column is refused, because a row with no columns shows nothing`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        store.setColumns(Watchlist.DEFAULT_LIST_ID, setOf(WatchlistColumn.LAST_PRICE))

        store.setColumns(Watchlist.DEFAULT_LIST_ID, emptySet())

        assertEquals(
            setOf(WatchlistColumn.LAST_PRICE),
            store.settings(Watchlist.DEFAULT_LIST_ID).first().columns,
        )
    }

    @Test
    fun `the active list falls back to the default one when the chosen list is gone`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        val second = store.create("نامزدها")
        store.setActiveList(second)
        assertEquals(second, store.activeListId().first())

        store.delete(second)

        assertEquals(Watchlist.DEFAULT_LIST_ID, store.activeListId().first())
    }

    @Test
    fun `starring writes to the list the reader is looking at`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        val second = store.create("نامزدها")
        store.setActiveList(second)

        store.toggle("BTCUSDT")

        assertEquals(emptyList<String>(), store.symbols(Watchlist.DEFAULT_LIST_ID).first())
        assertEquals(listOf("BTCUSDT"), store.symbols(second).first())
    }

    @Test
    fun `import strips comments and exchange prefixes and rejects nothing silently`() = runTest {
        val store = WatchlistStore(FakeDataStore())

        val result = store.importInto(
            Watchlist.DEFAULT_LIST_ID,
            """
            # فهرست صادرشده از تریدینگ‌ویو

            BINANCE:BTCUSDT
            binance:ethusdt
            SOLUSDT
            نماد فارسی
            MT5:FX:EURUSD
            BTCUSDT
            """.trimIndent(),
        )

        assertEquals(listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "EURUSD"), result.symbols)
        // The one line that carried something and produced no ticker comes back verbatim, so the
        // screen can say which line it was rather than quietly dropping it.
        assertEquals(listOf("نماد فارسی"), result.rejected)
        assertEquals(
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "EURUSD"),
            store.symbols(Watchlist.DEFAULT_LIST_ID).first(),
        )
    }

    @Test
    fun `import appends rather than replacing what the reader already had`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        store.add(Watchlist.DEFAULT_LIST_ID, "SOLUSDT")

        store.importInto(Watchlist.DEFAULT_LIST_ID, "BTCUSDT\nSOLUSDT\n")

        assertEquals(
            listOf("SOLUSDT", "BTCUSDT"),
            store.symbols(Watchlist.DEFAULT_LIST_ID).first(),
        )
    }

    @Test
    fun `an exported list reads back through import as the same list`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        val original = listOf("BTCUSDT", "ETHUSDT", "EUR/USD", "BRK.B")
        original.forEach { store.add(Watchlist.DEFAULT_LIST_ID, it) }

        val text = store.export(Watchlist.DEFAULT_LIST_ID)
        val target = store.create("وارد شده")
        val result = store.importInto(target, text)

        assertEquals(original, result.symbols)
        assertEquals(emptyList<String>(), result.rejected)
        assertEquals(original, store.symbols(target).first())
    }

    @Test
    fun `a list stops at a thousand symbols and says what did not fit`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        // One over the ceiling, which is TradingView's paid-tier ceiling and deliberately matched.
        val pasted = (1..WatchlistStore.MAX_SYMBOLS + 1).joinToString("\n") { "SYM$it" }

        val result = store.importInto(Watchlist.DEFAULT_LIST_ID, pasted)

        assertEquals(WatchlistStore.MAX_SYMBOLS, store.symbols(Watchlist.DEFAULT_LIST_ID).first().size)
        assertEquals(listOf("SYM1001"), result.rejected)

        // And the cap holds against the star as well as against a paste.
        store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")
        assertEquals(WatchlistStore.MAX_SYMBOLS, store.symbols(Watchlist.DEFAULT_LIST_ID).first().size)
    }

    @Test
    fun `parsing is pure, so a pasted file can be inspected before anything is written`() {
        val parsed = WatchlistTransfer.parse("#note\nNASDAQ:AAPL\n\n  msft \n@@@\n")

        assertEquals(listOf("AAPL", "MSFT"), parsed.symbols)
        assertEquals(listOf("@@@"), parsed.rejected)
    }

    @Test
    fun `export writes the plain tickers, one per line`() {
        assertEquals("BTCUSDT\nETHUSDT\n", WatchlistTransfer.format(listOf("BTCUSDT", "ETHUSDT")))
    }

    @Test
    fun `every flag and every column has an id no two of them share`() {
        assertEquals(WatchlistFlag.entries.size, WatchlistFlag.entries.map { it.id }.toSet().size)
        assertEquals(WatchlistColumn.entries.size, WatchlistColumn.entries.map { it.id }.toSet().size)
        // The one that would silently repaint stored rows if it ever changed.
        assertEquals(WatchlistFlag.BLUE, WatchlistFlag.ofId("blue"))
        assertNull(WatchlistFlag.ofId("teal"))
    }

    @Test
    fun `the default list id is the one watchlist alerts already resolve through`() {
        // If these ever drift, every alert scoped to a watchlist resolves to nothing and fires
        // never, while looking exactly like an alert that works.
        // Spelled out rather than compared to each other: the two are the same constant, so
        // asserting one against the other would compile away to a tautology and catch nothing.
        assertEquals("default", AlertScope.Watchlist.DEFAULT_LIST_ID)
        assertEquals("default", Watchlist.DEFAULT_LIST_ID)
    }
}

/** Enough of DataStore to exercise the store without a file on disk. */
private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
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
