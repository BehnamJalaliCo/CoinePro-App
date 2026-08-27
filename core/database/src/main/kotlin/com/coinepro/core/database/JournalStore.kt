package com.coinepro.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One trade the reader wrote down.
 *
 * **The reader's, not the server's.** The signals list already holds what the service published and
 * the portfolio holds what the broker executed; this holds what the *reader* thought — why they
 * took it, how they felt, what they would do differently. Neither backend has a journal route and
 * neither should: a trading diary is the one record whose value depends on nobody else reading it,
 * and a note written to be uploaded is a different note.
 *
 * Room rather than preferences, because a journal is a list that is searched, filtered and counted,
 * and grows without limit. It is also the reason the whole thing survives a reinstall only if the
 * reader exports it — which the screen offers, in the format a spreadsheet opens.
 *
 * Prices are nullable throughout. A reader jotting down "took the EURUSD long, was impatient" at
 * the moment it closed has given the journal the part that matters; demanding four numbers before
 * it will save is how a journal stops being kept.
 */
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    /** True for a buy. Stored rather than derived: entry and exit may both be absent. */
    val buy: Boolean,
    val entry: Double?,
    val exit: Double?,
    val size: Double?,
    /** Money, as the reader recorded it. Never computed from entry and exit — see the controller. */
    val pnl: Double?,
    /** One of the six, or blank. Free text is deliberately not offered: see the controller. */
    val emotion: String,
    val note: String,
    val lesson: String,
    /** Comma-separated. A journal's tags are read as a set and never joined against anything. */
    val tags: String,
    val createdAtEpochMillis: Long,
)

@Dao
interface JournalDao {
    /** Newest first: a journal is read backwards, from what just happened. */
    @Query("SELECT * FROM journal_entries ORDER BY createdAtEpochMillis DESC")
    fun entries(): Flow<List<JournalEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntryEntity): Long

    @Delete
    suspend fun delete(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries")
    suspend fun clear()
}
