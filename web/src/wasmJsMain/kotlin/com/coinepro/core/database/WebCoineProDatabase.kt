@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.core.database

import com.coinepro.web.wire.WireJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/*
 * The phone's database, in the browser: the same entities and the same DAO contracts, each table a
 * list kept in memory and written to the page's `localStorage` as JSON after every change. The SQL
 * in the phone's `@Query` annotations is carried out here by hand, one DAO method at a time, with
 * the same ordering, the same limits and the same replace-on-conflict rule.
 *
 * Registered with `WebRoom` by the page at start, so `CoineProDatabaseFactory.create` — the phone's
 * own factory — returns this.
 */

private fun readJs(key: String): String? = js("(function () { try { return localStorage.getItem(key); } catch (e) { return null; } })()")
private fun writeJs(key: String, value: String): Unit = js("(function () { try { localStorage.setItem(key, value); } catch (e) {} })()")
private fun removeJs(key: String): Unit = js("(function () { try { localStorage.removeItem(key); } catch (e) {} })()")

/** One table: rows in memory, mirrored to storage. Writes replace a row with the same key. */
internal class WebTable<T, K>(private val storageKey: String, serializer: KSerializer<T>, private val keyOf: (T) -> K) {
    private val listSerializer = ListSerializer(serializer)
    private val state = MutableStateFlow(load())
    val rows: List<T> get() = state.value
    val flow: Flow<List<T>> = state.asStateFlow()

    private fun load(): List<T> = readJs(storageKey)?.let { raw ->
        runCatching { WireJson.decodeFromString(listSerializer, raw) }.getOrNull()
    } ?: emptyList()

    fun set(rows: List<T>) {
        state.value = rows
        if (rows.isEmpty()) removeJs(storageKey) else writeJs(storageKey, WireJson.encodeToString(listSerializer, rows))
    }

    fun upsert(items: List<T>) {
        if (items.isEmpty()) return
        val incoming = items.associateBy(keyOf)
        set(rows.filterNot { keyOf(it) in incoming } + incoming.values)
    }

    fun removeWhere(predicate: (T) -> Boolean) = set(rows.filterNot(predicate))
}

class WebCoineProDatabase : CoineProDatabase() {
    private val cache = WebCacheDao()
    private val journal = WebJournalDao()
    private val paper = WebPaperTradeDao()
    private val scripts = WebSavedScriptDao()
    private val candles = WebCandleCacheDao()

    override fun cacheDao(): CoineProCacheDao = cache
    override fun journalDao(): JournalDao = journal
    override fun paperTradeDao(): PaperTradeDao = paper
    override fun savedScriptDao(): SavedScriptDao = scripts
    override fun candleCacheDao(): CandleCacheDao = candles
}

private class WebCacheDao : CoineProCacheDao() {
    private val quotes = WebTable("db:cached_market_quotes", CachedMarketQuoteEntity.serializer()) { it.symbol }
    private val signals = WebTable("db:cached_signal_history", CachedSignalEntity.serializer()) { it.id }
    private val targets = WebTable("db:cached_signal_targets", CachedSignalTargetEntity.serializer()) { it.signalId to it.level }
    private val metadata = WebTable("db:cache_metadata", CacheMetadataEntity.serializer()) { it.key }

    override suspend fun marketQuotes(): List<CachedMarketQuoteEntity> = quotes.rows.sortedBy { it.symbol }
    override suspend fun signalHistory(): List<CachedSignalEntity> = signals.rows.sortedByDescending { it.id }
    override suspend fun signalTargets(): List<CachedSignalTargetEntity> =
        targets.rows.sortedWith(compareBy({ it.signalId }, { it.level }))
    override suspend fun metadata(key: String): CacheMetadataEntity? = metadata.rows.firstOrNull { it.key == key }
    override suspend fun insertMarketQuotes(items: List<CachedMarketQuoteEntity>) = quotes.upsert(items)
    override suspend fun insertSignalHistory(items: List<CachedSignalEntity>) = signals.upsert(items)
    override suspend fun insertSignalTargets(items: List<CachedSignalTargetEntity>) = targets.upsert(items)
    override suspend fun upsertMetadata(value: CacheMetadataEntity) = metadata.upsert(listOf(value))
    override suspend fun deleteMarketQuotes() = quotes.set(emptyList())
    override suspend fun deleteSignalTargets() = targets.set(emptyList())
    override suspend fun deleteSignalHistory() = signals.set(emptyList())
    override suspend fun deleteMetadata(key: String) = metadata.removeWhere { it.key == key }
}

/** `autoGenerate = true`: a zero id takes the next one, as SQLite's rowid does. */
private fun nextId(existing: List<Long>): Long = (existing.maxOrNull() ?: 0L) + 1L

private class WebJournalDao : JournalDao {
    private val table = WebTable("db:journal_entries", JournalEntryEntity.serializer()) { it.id }
    override fun entries(): Flow<List<JournalEntryEntity>> =
        kotlinx.coroutines.flow.flow { table.flow.collect { rows -> emit(rows.sortedByDescending { it.createdAtEpochMillis }) } }
    override suspend fun insert(entry: JournalEntryEntity): Long {
        val row = if (entry.id == 0L) entry.copy(id = nextId(table.rows.map { it.id })) else entry
        table.upsert(listOf(row))
        return row.id
    }
    override suspend fun delete(entry: JournalEntryEntity) = table.removeWhere { it.id == entry.id }
    override suspend fun clear() = table.set(emptyList())
}

private class WebPaperTradeDao : PaperTradeDao {
    private val table = WebTable("db:paper_trades", PaperTradeEntity.serializer()) { it.id }
    override fun trades(): Flow<List<PaperTradeEntity>> =
        kotlinx.coroutines.flow.flow { table.flow.collect { rows -> emit(rows.sortedByDescending { it.openedAtEpochMillis }) } }
    override suspend fun insert(trade: PaperTradeEntity): Long {
        val row = if (trade.id == 0L) trade.copy(id = nextId(table.rows.map { it.id })) else trade
        table.upsert(listOf(row))
        return row.id
    }
    override suspend fun update(trade: PaperTradeEntity) {
        if (table.rows.any { it.id == trade.id }) table.upsert(listOf(trade))
    }
    override suspend fun clear() = table.set(emptyList())
}

private class WebSavedScriptDao : SavedScriptDao {
    private val table = WebTable("db:saved_scripts", SavedScriptEntity.serializer()) { it.id }
    override fun scripts(): Flow<List<SavedScriptEntity>> =
        kotlinx.coroutines.flow.flow { table.flow.collect { rows -> emit(rows.sortedByDescending { it.updatedAtEpochMillis }) } }
    override suspend fun byId(id: Long): SavedScriptEntity? = table.rows.firstOrNull { it.id == id }
    override suspend fun count(): Int = table.rows.size
    override suspend fun insert(script: SavedScriptEntity): Long {
        val row = if (script.id == 0L) script.copy(id = nextId(table.rows.map { it.id })) else script
        table.upsert(listOf(row))
        return row.id
    }
    override suspend fun update(script: SavedScriptEntity) {
        if (table.rows.any { it.id == script.id }) table.upsert(listOf(script))
    }
    override suspend fun delete(id: Long) = table.removeWhere { it.id == id }
}

private class WebCandleCacheDao : CandleCacheDao {
    private val table = WebTable("db:cached_candles", CachedCandleEntity.serializer()) { Triple(it.symbol, it.interval, it.t) }

    private fun series(symbol: String, interval: String) = table.rows.filter { it.symbol == symbol && it.interval == interval }

    override suspend fun newest(symbol: String, intervalWire: String, limit: Int): List<CachedCandleEntity> =
        series(symbol, intervalWire).sortedByDescending { it.t }.take(limit)
    override suspend fun upsert(candles: List<CachedCandleEntity>) = table.upsert(candles)
    override suspend fun before(symbol: String, intervalWire: String, before: Long, limit: Int): List<CachedCandleEntity> =
        series(symbol, intervalWire).filter { it.t < before }.sortedByDescending { it.t }.take(limit)
    override suspend fun span(symbol: String, intervalWire: String): CachedSpanRow? {
        val rows = series(symbol, intervalWire)
        // `COUNT(*)` with `MIN`/`MAX` always answers one row; with no bars the extremes are NULL.
        return CachedSpanRow(rows.size, rows.minOfOrNull { it.t } ?: 0L, rows.maxOfOrNull { it.t } ?: 0L)
    }
    override suspend fun storedAt(symbol: String, intervalWire: String): Long? =
        series(symbol, intervalWire).maxOfOrNull { it.cachedAtEpochMillis }
    override suspend fun totalBars(): Int = table.rows.size
    override suspend fun seriesByAge(): List<CachedSeriesRow> = table.rows
        .groupBy { it.symbol to it.interval }
        .map { (key, rows) -> CachedSeriesRow(key.first, key.second, rows.maxOf { it.cachedAtEpochMillis }, rows.size) }
        .sortedBy { it.touchedAt }
    override suspend fun clearSeries(symbol: String, intervalWire: String) =
        table.removeWhere { it.symbol == symbol && it.interval == intervalWire }
    override suspend fun trim(symbol: String, intervalWire: String, keep: Int) {
        val kept = series(symbol, intervalWire).sortedByDescending { it.t }.take(keep).map { it.t }.toSet()
        table.removeWhere { it.symbol == symbol && it.interval == intervalWire && it.t !in kept }
    }
    override suspend fun clearAll() = table.set(emptyList())
}
