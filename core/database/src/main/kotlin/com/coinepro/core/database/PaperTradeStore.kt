package com.coinepro.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A trade taken with no money.
 *
 * Separate from the journal on purpose. A journal entry is a finished thing the reader wrote down;
 * a paper trade is *open* — it has to be marked against a live price, and it closes at whatever the
 * market was doing at the moment the reader pressed close. Sharing a table would mean a row that is
 * sometimes a record and sometimes a position.
 *
 * [entry] and [size] are required here where the journal makes everything optional, and the reason
 * is the same in both cases: a paper trade with no entry price cannot be marked, so it would be a
 * position whose P&L is permanently unknown. A journal is a note; this is a simulation, and a
 * simulation that cannot compute is not one.
 */
@Entity(tableName = "paper_trades")
data class PaperTradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val buy: Boolean,
    val entry: Double,
    val size: Double,
    val openedAtEpochMillis: Long,
    /** Null while open. Set once, from the live price at the moment of closing. */
    val exit: Double? = null,
    val closedAtEpochMillis: Long? = null,
)

@Dao
interface PaperTradeDao {
    @Query("SELECT * FROM paper_trades ORDER BY openedAtEpochMillis DESC")
    fun trades(): Flow<List<PaperTradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trade: PaperTradeEntity): Long

    @Update
    suspend fun update(trade: PaperTradeEntity)

    @Query("DELETE FROM paper_trades")
    suspend fun clear()
}
