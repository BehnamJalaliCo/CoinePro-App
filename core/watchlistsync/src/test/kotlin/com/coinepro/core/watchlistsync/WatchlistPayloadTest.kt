package com.coinepro.core.watchlistsync

import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistColumn
import com.coinepro.core.datastore.WatchlistFlag
import com.coinepro.core.datastore.WatchlistSettings
import com.coinepro.core.datastore.WatchlistSnapshot
import com.coinepro.core.datastore.WatchlistSort
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The document nobody validates.
 *
 * The server stores this payload and hands it back without ever reading it, which means there is
 * no second opinion anywhere in the system about whether it is well formed. A shape change that
 * survives a code review and fails here would be discovered by a reader whose lists came back
 * empty on a new phone.
 */
class WatchlistPayloadTest {

    private val snapshot = WatchlistSnapshot(
        lists = listOf(
            Watchlist("default", "دیده‌بان", listOf("BTCUSDT", "ETHUSDT"), 1_000L, 2_000L),
            Watchlist("list_a", "طلا و نقره", listOf("XAUUSD"), 3_000L, 4_000L),
        ),
        settings = mapOf(
            "default" to WatchlistSettings(
                flags = mapOf("BTCUSDT" to WatchlistFlag.RED, "ETHUSDT" to WatchlistFlag.GREY),
                columns = setOf(WatchlistColumn.LAST_PRICE, WatchlistColumn.VOLUME),
                sort = WatchlistSort(WatchlistColumn.CHANGE_PERCENT, descending = false),
            ),
        ),
        tombstones = mapOf("list_gone" to 5_000L),
    )

    @Test
    fun `everything a reader built round-trips unchanged`() {
        val decoded = WatchlistPayload.decode(WatchlistPayload.encode(snapshot))

        assertEquals(snapshot.lists, decoded.lists)
        assertEquals(snapshot.settings, decoded.settings)
        assertEquals(snapshot.tombstones, decoded.tombstones)
    }

    @Test
    fun `a list still on the defaults carries no settings at all`() {
        val encoded = WatchlistPayload.encode(snapshot)
        val entry = encoded.getAsJsonArray("lists")
            .map { it.asJsonObject }
            .single { it.get("id").asString == "list_a" }

        // Fifty lists' worth of default column sets is most of the way to the 64 KB cap for
        // information that is already the default on the far side.
        assertTrue(entry.keySet().none { it == "flags" || it == "columns" || it == "sort_column" })
        assertEquals(WatchlistSettings(), WatchlistPayload.decode(encoded).settings["list_a"] ?: WatchlistSettings())
    }

    @Test
    fun `the reader's own order is an absent key, not a null one`() {
        val plain = WatchlistSnapshot(lists = listOf(Watchlist("default", "د")))
        val entry = WatchlistPayload.encode(plain).getAsJsonArray("lists").single().asJsonObject

        assertTrue("sort_column" !in entry.keySet())
        assertTrue(WatchlistPayload.decode(WatchlistPayload.encode(plain)).lists.single().symbols.isEmpty())
    }

    @Test
    fun `a reader who has never synced decodes to nothing rather than to damage`() {
        assertEquals(WatchlistSnapshot(), WatchlistPayload.decode(null))
        assertEquals(WatchlistSnapshot(), WatchlistPayload.decode(JsonObject()))
    }

    @Test
    fun `a document from a schema this build does not know is treated as empty`() {
        val future = JsonParser.parseString(
            """{"schema": 99, "lists": [{"id": "list_a", "name": "x"}]}""",
        ).asJsonObject

        // Not parsed optimistically. This build would otherwise write its guess at the newer shape
        // back over the newer phone's real document.
        assertEquals(WatchlistSnapshot(), WatchlistPayload.decode(future))
    }

    @Test
    fun `a colour or column this build does not know is dropped, never substituted`() {
        val stored = JsonParser.parseString(
            """
            {"schema": 1, "lists": [{
              "id": "list_a", "name": "x", "symbols": ["BTCUSDT", "ETHUSDT"],
              "flags": {"BTCUSDT": "teal", "ETHUSDT": "blue"},
              "columns": ["last", "spread"]
            }]}
            """.trimIndent(),
        ).asJsonObject

        val settings = WatchlistPayload.decode(stored).settings.getValue("list_a")

        assertEquals(mapOf("ETHUSDT" to WatchlistFlag.BLUE), settings.flags)
        assertEquals(setOf(WatchlistColumn.LAST_PRICE), settings.columns)
    }

    @Test
    fun `a list with no id is skipped rather than given one`() {
        val stored = JsonParser.parseString(
            """{"schema": 1, "lists": [{"name": "x"}, {"id": "list_a", "name": "y"}]}""",
        ).asJsonObject

        // An invented id would arrive as a brand-new list on every single sync.
        assertEquals(listOf("list_a"), WatchlistPayload.decode(stored).lists.map { it.id })
    }

    @Test
    fun `size is counted in bytes of UTF-8, not in characters`() {
        val persian = WatchlistSnapshot(lists = listOf(Watchlist("default", "دیده‌بان")))
        val encoded = WatchlistPayload.encode(persian)

        // Two bytes per Persian letter. A cap checked against the character count would pass a
        // document the server is certain to refuse.
        assertTrue(WatchlistPayload.sizeInBytes(encoded) > encoded.toString().length)
    }

    @Test
    fun `the active list is deliberately not in the document`() {
        // Which list is on screen belongs to one device at one moment. Syncing it would move the
        // list under a reader's finger because their other phone was looking somewhere else.
        assertTrue("active" !in WatchlistPayload.encode(snapshot).keySet())
    }
}
