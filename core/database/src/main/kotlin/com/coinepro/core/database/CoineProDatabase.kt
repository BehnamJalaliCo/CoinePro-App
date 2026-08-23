package com.coinepro.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "cached_market_quotes")
data class CachedMarketQuoteEntity(
    @PrimaryKey val symbol: String,
    val displayName: String,
    val marketType: String,
    val price: Double,
    val bid: Double?,
    val ask: Double?,
    val changePercent: Double?,
    val source: String,
    val sourceTimestampEpochMillis: Long,
)

@Entity(tableName = "cached_signal_history")
data class CachedSignalEntity(
    @PrimaryKey val id: Long,
    val market: String,
    val symbol: String,
    val direction: String,
    val status: String,
    val timeframe: String?,
    val strategy: String?,
    val confidence: Int?,
    val entry: Double?,
    val entryZoneLow: Double?,
    val entryZoneHigh: Double?,
    val stopLoss: Double?,
    val riskRewardTp1: Double?,
    val rationale: String?,
    val scoreTechnical: Double?,
    val scorePattern: Double?,
    val scoreMl: Double?,
    val closeReason: String?,
    val resultPnlUsd: Double?,
    val resultSource: String?,
    val createdAt: String?,
    val closedAt: String?,
)

@Entity(
    tableName = "cached_signal_targets",
    primaryKeys = ["signalId", "level"],
)
data class CachedSignalTargetEntity(
    val signalId: Long,
    val level: Int,
    val price: Double?,
    val hit: Boolean?,
)

@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    @PrimaryKey val key: String,
    val expectedTotal: Int?,
    val coverageComplete: Boolean?,
    val cachedAtEpochMillis: Long,
)

@Dao
abstract class CoineProCacheDao {
    @Query("SELECT * FROM cached_market_quotes ORDER BY symbol")
    abstract suspend fun marketQuotes(): List<CachedMarketQuoteEntity>

    @Query("SELECT * FROM cached_signal_history ORDER BY id DESC")
    abstract suspend fun signalHistory(): List<CachedSignalEntity>

    @Query("SELECT * FROM cached_signal_targets ORDER BY signalId, level")
    abstract suspend fun signalTargets(): List<CachedSignalTargetEntity>

    @Query("SELECT * FROM cache_metadata WHERE `key` = :key LIMIT 1")
    abstract suspend fun metadata(key: String): CacheMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMarketQuotes(items: List<CachedMarketQuoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSignalHistory(items: List<CachedSignalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSignalTargets(items: List<CachedSignalTargetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMetadata(value: CacheMetadataEntity)

    @Query("DELETE FROM cached_market_quotes")
    abstract suspend fun deleteMarketQuotes()

    @Query("DELETE FROM cached_signal_targets")
    abstract suspend fun deleteSignalTargets()

    @Query("DELETE FROM cached_signal_history")
    abstract suspend fun deleteSignalHistory()

    @Query("DELETE FROM cache_metadata WHERE `key` = :key")
    abstract suspend fun deleteMetadata(key: String)

    @Transaction
    open suspend fun replaceMarketQuotes(
        items: List<CachedMarketQuoteEntity>,
        metadata: CacheMetadataEntity,
    ) {
        deleteMarketQuotes()
        if (items.isNotEmpty()) insertMarketQuotes(items)
        upsertMetadata(metadata)
    }

    @Transaction
    open suspend fun replaceSignalHistory(
        signals: List<CachedSignalEntity>,
        targets: List<CachedSignalTargetEntity>,
        metadata: CacheMetadataEntity,
    ) {
        deleteSignalTargets()
        deleteSignalHistory()
        if (signals.isNotEmpty()) insertSignalHistory(signals)
        if (targets.isNotEmpty()) insertSignalTargets(targets)
        upsertMetadata(metadata)
    }

    @Transaction
    open suspend fun clearMarketCache() {
        deleteMarketQuotes()
        deleteMetadata(MARKET_METADATA_KEY)
    }

    @Transaction
    open suspend fun clearSignalHistoryCache() {
        deleteSignalTargets()
        deleteSignalHistory()
        deleteMetadata(SIGNAL_HISTORY_METADATA_KEY)
    }
}

@Database(
    entities = [
        CachedMarketQuoteEntity::class,
        CachedSignalEntity::class,
        CachedSignalTargetEntity::class,
        CacheMetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CoineProDatabase : RoomDatabase() {
    abstract fun cacheDao(): CoineProCacheDao
}

object CoineProDatabaseFactory {
    fun create(context: Context): CoineProDatabase = Room.databaseBuilder(
        context.applicationContext,
        CoineProDatabase::class.java,
        "coinepro-read-cache.db",
    ).build()
}

internal const val MARKET_METADATA_KEY = "market_snapshot"
internal const val SIGNAL_HISTORY_METADATA_KEY = "signal_history"
