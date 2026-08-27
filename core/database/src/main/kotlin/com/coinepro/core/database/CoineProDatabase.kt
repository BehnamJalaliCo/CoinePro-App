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
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
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
        JournalEntryEntity::class,
        PaperTradeEntity::class,
    ],
    // Bumped for the journal table. `fallbackToDestructiveMigration` is deliberately *not* used:
    // every other table here is a cache that can be refetched, and the journal is the one thing in
    // this database that cannot. See the migration below.
    version = 3,
    exportSchema = false,
)
abstract class CoineProDatabase : RoomDatabase() {
    abstract fun cacheDao(): CoineProCacheDao
    abstract fun journalDao(): JournalDao
    abstract fun paperTradeDao(): PaperTradeDao
}

/**
 * Version 1 to 2: the journal table.
 *
 * Written out rather than letting Room destroy and recreate. Every other table in this database is
 * a cache and losing one costs a refetch; the journal is the reader's own writing and losing it is
 * losing something nobody can give back.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS journal_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                symbol TEXT NOT NULL,
                buy INTEGER NOT NULL,
                entry REAL,
                exit REAL,
                size REAL,
                pnl REAL,
                emotion TEXT NOT NULL,
                note TEXT NOT NULL,
                lesson TEXT NOT NULL,
                tags TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/** Version 2 to 3: paper trades. Written out for the same reason as the journal's. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS paper_trades (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                symbol TEXT NOT NULL,
                buy INTEGER NOT NULL,
                entry REAL NOT NULL,
                size REAL NOT NULL,
                openedAtEpochMillis INTEGER NOT NULL,
                exit REAL,
                closedAtEpochMillis INTEGER
            )
            """.trimIndent(),
        )
    }
}

object CoineProDatabaseFactory {
    fun create(context: Context): CoineProDatabase = Room.databaseBuilder(
        context.applicationContext,
        CoineProDatabase::class.java,
        "coinepro-read-cache.db",
    )
        // The journal migration is registered rather than the database being allowed to fall back
        // to destructive recreation. Every other table here is a cache; the journal is not.
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
}

internal const val MARKET_METADATA_KEY = "market_snapshot"
internal const val SIGNAL_HISTORY_METADATA_KEY = "signal_history"
