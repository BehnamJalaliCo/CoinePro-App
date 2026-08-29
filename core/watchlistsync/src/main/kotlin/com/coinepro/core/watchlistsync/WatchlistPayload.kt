package com.coinepro.core.watchlistsync

import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistColumn
import com.coinepro.core.datastore.WatchlistFlag
import com.coinepro.core.datastore.WatchlistSettings
import com.coinepro.core.datastore.WatchlistSnapshot
import com.coinepro.core.datastore.WatchlistSort
import com.coinepro.core.datastore.WatchlistStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * The reader's watchlists, in the shape they travel in.
 *
 * ### The server does not read this
 *
 * `GET`/`PUT /api/mobile/v1/watchlists` stores the payload and hands it back. It validates two
 * things and nothing else: that it is a JSON **object**, and that it is under **64 KB**. It never
 * looks inside. So the whole structure below is this app's to define, and — because nothing on the
 * far side will ever reject a bad one — this file is the only thing standing between a shape
 * change and a document a future build cannot read. Hence [SCHEMA], hence the tolerance in
 * [decode], and hence the round-trip test.
 *
 * ### The shape
 *
 * ```json
 * {
 *   "schema": 1,
 *   "lists": [
 *     {
 *       "id": "list_9f2c1a04bb71",
 *       "name": "طلا و نقره",
 *       "symbols": ["XAUUSD", "XAGUSD"],
 *       "created_at_ms": 1730000000000,
 *       "updated_at_ms": 1730500000000,
 *       "flags": { "XAUUSD": "red" },
 *       "columns": ["flag", "last", "change_percent"],
 *       "sort_column": "change_percent",
 *       "sort_descending": true
 *     }
 *   ],
 *   "deleted": { "list_1188aa93c002": 1730400000000 }
 * }
 * ```
 *
 * Every field is the stored form of something already in `core:datastore`, spelled with that
 * module's own stable ids — `WatchlistFlag.id`, `WatchlistColumn.id` — and never with an ordinal.
 * An ordinal would repaint every flag on every other device the day an eighth colour is inserted
 * in the middle of the enum.
 *
 * `sort_column` is **absent** rather than null when the reader is on their own dragged order,
 * because that is the common case and an absent key costs nothing; `flags` and `columns` are absent
 * when a list is still on the defaults, which is what keeps fifty lists inside 64 KB.
 *
 * `deleted` is the tombstone map: a deleted list's id against the moment it was deleted. It is the
 * only part of this document that describes something that is *not* there, and the merge cannot
 * work without it — see [WatchlistMerge].
 *
 * ### The active list is not here, on purpose
 *
 * Which list is on screen is a fact about one device at one moment. Syncing it would move the list
 * under a reader's finger because their other phone was looking somewhere else, which is the exact
 * class of surprise this feature is not allowed to produce.
 */
object WatchlistPayload {

    /**
     * The version of the shape above.
     *
     * Written on every upload and checked on every read. A document from a future build — a schema
     * this one does not recognise — is treated as **empty** rather than parsed optimistically, so
     * an older app on a second phone contributes its own lists to the merge and never rewrites the
     * newer document into a shape it invented. That costs the older phone the newer phone's data
     * until it updates, which is a visible and recoverable failure; guessing at unknown fields and
     * writing the guess back is neither.
     */
    const val SCHEMA = 1

    fun encode(snapshot: WatchlistSnapshot): JsonObject {
        val root = JsonObject()
        root.addProperty(SCHEMA_KEY, SCHEMA)
        val lists = JsonArray()
        snapshot.lists.take(WatchlistStore.MAX_LISTS).forEach { list ->
            lists.add(encodeList(list, snapshot.settings[list.id]))
        }
        root.add(LISTS_KEY, lists)
        if (snapshot.tombstones.isNotEmpty()) {
            val deleted = JsonObject()
            snapshot.tombstones.forEach { (id, at) -> deleted.addProperty(id, at) }
            root.add(DELETED_KEY, deleted)
        }
        return root
    }

    /**
     * Reads a stored document back.
     *
     * Every field is optional and every unreadable one is dropped rather than defaulted, because
     * this document has spent time on a server that never validated it and may have been written by
     * a build that is one release ahead or three behind. A list with no id is skipped outright — an
     * id is the only thing a merge can key on, and a list without one would arrive as a duplicate
     * on every single sync.
     *
     * A null or empty payload is a first-ever reader, not damage: the route answers a reader who
     * has never synced with `{}` and version zero rather than with a 404.
     */
    fun decode(payload: JsonObject?): WatchlistSnapshot {
        if (payload == null || payload.size() == 0) return WatchlistSnapshot()
        if (payload.int(SCHEMA_KEY) != SCHEMA) return WatchlistSnapshot()
        val lists = mutableListOf<Watchlist>()
        val settings = mutableMapOf<String, WatchlistSettings>()
        payload.get(LISTS_KEY)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { element -> element.isJsonObject }?.asJsonObject }
            ?.forEach { entry ->
                val id = entry.string(ID_KEY) ?: return@forEach
                if (lists.any { it.id == id }) return@forEach
                lists += Watchlist(
                    id = id,
                    // A stored list with no readable name falls back to its id for the same reason
                    // the store's own decoder does: a blank chip is one the reader cannot tell from
                    // its neighbours, and there is no way back from that on screen.
                    name = entry.string(NAME_KEY) ?: id,
                    symbols = entry.strings(SYMBOLS_KEY).distinct().take(WatchlistStore.MAX_SYMBOLS),
                    createdAt = entry.long(CREATED_KEY),
                    updatedAt = entry.long(UPDATED_KEY),
                )
                val decoded = decodeSettings(entry)
                if (decoded != WatchlistSettings()) settings[id] = decoded
            }
        val deleted = payload.get(DELETED_KEY)?.takeIf { it.isJsonObject }?.asJsonObject
            ?.entrySet()
            ?.mapNotNull { (id, value) ->
                val at = value.takeIf { it.isJsonPrimitive }?.runCatching { asLong }?.getOrNull()
                if (id.isBlank() || at == null || at <= 0L) null else id to at
            }
            ?.toMap()
            .orEmpty()
        return WatchlistSnapshot(lists = lists, settings = settings, tombstones = deleted)
    }

    /**
     * How large this document is on the wire.
     *
     * Measured in **bytes of UTF-8**, which is the only unit the 64 KB cap can mean and is nothing
     * like the character count: a Persian list name is two bytes per letter, so a payload counted
     * in characters would pass this check at forty thousand and be refused by the server.
     */
    fun sizeInBytes(payload: JsonObject): Int = payload.toString().toByteArray(Charsets.UTF_8).size

    private fun encodeList(list: Watchlist, settings: WatchlistSettings?): JsonObject {
        val entry = JsonObject()
        entry.addProperty(ID_KEY, list.id)
        entry.addProperty(NAME_KEY, list.name)
        val symbols = JsonArray()
        list.symbols.forEach(symbols::add)
        entry.add(SYMBOLS_KEY, symbols)
        entry.addProperty(CREATED_KEY, list.createdAt)
        entry.addProperty(UPDATED_KEY, list.updatedAt)
        if (settings == null) return entry
        if (settings.flags.isNotEmpty()) {
            val flags = JsonObject()
            settings.flags.forEach { (symbol, flag) -> flags.addProperty(symbol, flag.id) }
            entry.add(FLAGS_KEY, flags)
        }
        if (settings.columns != WatchlistColumn.DEFAULT) {
            val columns = JsonArray()
            settings.columns.forEach { columns.add(JsonPrimitive(it.id)) }
            entry.add(COLUMNS_KEY, columns)
        }
        settings.sort.column?.let { entry.addProperty(SORT_COLUMN_KEY, it.id) }
        if (!settings.sort.descending) entry.addProperty(SORT_DESCENDING_KEY, false)
        return entry
    }

    private fun decodeSettings(entry: JsonObject): WatchlistSettings {
        val flags = entry.get(FLAGS_KEY)?.takeIf { it.isJsonObject }?.asJsonObject
            ?.entrySet()
            ?.mapNotNull { (symbol, value) ->
                // A colour this build does not know is dropped, not substituted. Substituting would
                // repaint a row in a colour the reader never chose and cannot account for; dropping
                // leaves it unflagged, which is at least true.
                val flag = value.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.let(WatchlistFlag::ofId)
                if (symbol.isBlank() || flag == null) null else symbol to flag
            }
            ?.toMap()
            .orEmpty()
        val columns = entry.strings(COLUMNS_KEY).mapNotNull(WatchlistColumn::ofId).toSet()
        return WatchlistSettings(
            flags = flags,
            // An unreadable or absent column set is the default set, never the empty one: a row
            // with no columns is a row with nothing on it but a logo.
            columns = columns.ifEmpty { WatchlistColumn.DEFAULT },
            sort = WatchlistSort(
                column = entry.string(SORT_COLUMN_KEY)?.let(WatchlistColumn::ofId),
                descending = entry.get(SORT_DESCENDING_KEY)
                    ?.takeIf { it.isJsonPrimitive }
                    ?.runCatching { asBoolean }
                    ?.getOrNull()
                    ?: true,
            ),
        )
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)

    private fun JsonObject.long(name: String): Long =
        get(name)?.takeIf { it.isJsonPrimitive }?.runCatching { asLong }?.getOrNull() ?: 0L

    private fun JsonObject.int(name: String): Int =
        get(name)?.takeIf { it.isJsonPrimitive }?.runCatching { asInt }?.getOrNull() ?: 0

    private fun JsonObject.strings(name: String): List<String> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { element -> element.isJsonPrimitive }?.asString }
            ?.filter(String::isNotBlank)
            .orEmpty()

    private const val SCHEMA_KEY = "schema"
    private const val LISTS_KEY = "lists"
    private const val DELETED_KEY = "deleted"
    private const val ID_KEY = "id"
    private const val NAME_KEY = "name"
    private const val SYMBOLS_KEY = "symbols"
    private const val CREATED_KEY = "created_at_ms"
    private const val UPDATED_KEY = "updated_at_ms"
    private const val FLAGS_KEY = "flags"
    private const val COLUMNS_KEY = "columns"
    private const val SORT_COLUMN_KEY = "sort_column"
    private const val SORT_DESCENDING_KEY = "sort_descending"
}
