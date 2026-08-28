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
 * Symbol **and** timeframe. The same instrument on two timeframes is two different series and
 * caching them under one key is how a reader ends up looking at hourly bars labelled daily.
 */
@Entity(
    tableName = "cached_candles",
    primaryKeys = ["symbol", "timeframe", "t"],
    indices = [Index(value = ["symbol", "timeframe", "t"])],
)
data class CachedCandleEntity(
    val symbol: String,
    val timeframe: String,
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
     */
    @Query(
        "SELECT * FROM cached_candles WHERE symbol = :symbol AND timeframe = :timeframe " +
            "ORDER BY t DESC LIMIT :limit",
    )
    suspend fun newest(symbol: String, timeframe: String, limit: Int): List<CachedCandleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(candles: List<CachedCandleEntity>)

    @Query("DELETE FROM cached_candles WHERE symbol = :symbol AND timeframe = :timeframe")
    suspend fun clearSeries(symbol: String, timeframe: String)

    /**
     * Drop everything older than the newest [keep] bars of one series.
     *
     * Without this the table grows for ever: a reader who leaves the app on a one-minute chart
     * adds a row a minute, and paging back through history adds thousands at a time.
     */
    @Query(
        "DELETE FROM cached_candles WHERE symbol = :symbol AND timeframe = :timeframe AND t NOT IN " +
            "(SELECT t FROM cached_candles WHERE symbol = :symbol AND timeframe = :timeframe " +
            "ORDER BY t DESC LIMIT :keep)",
    )
    suspend fun trim(symbol: String, timeframe: String, keep: Int)

    @Query("DELETE FROM cached_candles")
    suspend fun clearAll()

    /**
     * Write and trim as one transaction.
     *
     * Separately, a process death between them leaves a table that grows without bound — the trim
     * is the half that never has a reason to run on its own.
     */
    @Transaction
    suspend fun replace(candles: List<CachedCandleEntity>, symbol: String, timeframe: String, keep: Int) {
        if (candles.isEmpty()) return
        upsert(candles)
        trim(symbol, timeframe, keep)
    }
}
