package com.coinepro.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * One cached bar.
 *
 * ### Why this table exists
 *
 * Because "the chart won't come up" is, by a factor of two and a half, the loudest complaint about
 * every app in this category — and in Persian-language reviews specifically it is **19.3%** of all
 * negative chart mentions, the largest single category by a wide margin. Before this table, every
 * chart open in this app was a network round trip watched through an empty rectangle, and every
 * return from the background on a cold process was the same.
 *
 * The fix is not a faster request. It is having something true to draw *before* the request is
 * made, and then correcting it.
 *
 * ### Why Room and not the preferences store
 *
 * Three hundred bars is a few thousand numbers, and the read has to land inside a frame or two.
 * DataStore hands back one blob it has to parse in full; this hands back the rows for one key,
 * indexed, without touching the other symbols. It also expires per row rather than per file.
 *
 * ### The key
 *
 * Symbol **and** interval. The same instrument on two intervals is two different series and caching
 * them under one key is how a reader ends up looking at hourly bars labelled daily.
 *
 * The interval column holds `ChartInterval.wire` — `"M5"`, `"H4"`, `"MN1"` for a preset and a bare
 * minute count such as `"205"` for one the reader typed. It was a `Timeframe` enum name until
 * version 6 of this database, which was correct only while every series had a preset to name it.
 * A custom interval has none, and forcing one to borrow a preset's key means two different series
 * silently overwrite each other: 205 minutes and 137 minutes both land in the same row, and
 * whichever was fetched last is drawn under both labels. Wire spellings cannot collide, because no
 * preset is spelled as a bare number — which is also why the migration that renamed this column
 * could copy every row across unchanged rather than dropping them.
 */
@Entity(
    tableName = "cached_candles",
    primaryKeys = ["symbol", "interval", "t"],
    indices = [Index(value = ["symbol", "interval", "t"])],
)
data class CachedCandleEntity(
    val symbol: String,
    /** `ChartInterval.wire`: a preset's canonical spelling, or a custom interval's minute count. */
    val interval: String,
    /** Bar open time, unix **seconds** — the same unit both backends send. */
    val t: Long,
    val o: Double,
    val h: Double,
    val l: Double,
    val c: Double,
    val v: Double,
    /** When this row was written, so a stale series can be recognised rather than trusted. */
    val cachedAtEpochMillis: Long,
)

@Dao
interface CandleCacheDao {

    /**
     * The newest [limit] bars for one series, oldest first.
     *
     * Ordered descending in SQL and reversed in Kotlin, because "the newest N" is what a chart
     * opens on and an index scan from the end is what makes that cheap. Reversing a few hundred
     * items in memory costs nothing next to reading rows the chart will not draw.
     *
     * [intervalWire] is spelled out rather than named `interval` so that the bound parameter and
     * the quoted column beside it stay distinguishable to a reader of the SQL.
     */
    @Query(
        "SELECT * FROM cached_candles WHERE symbol = :symbol AND `interval` = :intervalWire " +
            "ORDER BY t DESC LIMIT :limit",
    )
    suspend fun newest(symbol: String, intervalWire: String, limit: Int): List<CachedCandleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(candles: List<CachedCandleEntity>)

    @Query("DELETE FROM cached_candles WHERE symbol = :symbol AND `interval` = :intervalWire")
    suspend fun clearSeries(symbol: String, intervalWire: String)

    /**
     * Drop everything older than the newest [keep] bars of one series.
     *
     * Without this the table grows for ever: a reader who leaves the app on a one-minute chart
     * adds a row a minute, and paging back through history adds thousands at a time.
     */
    @Query(
        "DELETE FROM cached_candles WHERE symbol = :symbol AND `interval` = :intervalWire AND t NOT IN " +
            "(SELECT t FROM cached_candles WHERE symbol = :symbol AND `interval` = :intervalWire " +
            "ORDER BY t DESC LIMIT :keep)",
    )
    suspend fun trim(symbol: String, intervalWire: String, keep: Int)

    @Query("DELETE FROM cached_candles")
    suspend fun clearAll()

    /**
     * Write and trim as one transaction.
     *
     * Separately, a process death between them leaves a table that grows without bound — the trim
     * is the half that never has a reason to run on its own.
     */
    @Transaction
    suspend fun replace(candles: List<CachedCandleEntity>, symbol: String, intervalWire: String, keep: Int) {
        if (candles.isEmpty()) return
        upsert(candles)
        trim(symbol, intervalWire, keep)
    }
}
