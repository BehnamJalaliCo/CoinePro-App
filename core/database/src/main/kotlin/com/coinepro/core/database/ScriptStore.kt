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
 * A script the reader wrote, and the values they set for its inputs.
 *
 * This is writing, not a cache. Like the journal and unlike everything else in this database, there
 * is no server copy and no way to refetch it — which is why the migration below is written out by
 * hand rather than letting Room recreate the schema.
 *
 * [presetId] records which shipped preset a script started life as, or null for one written from
 * scratch. It is kept so the library can say "based on «تقاطع دو میانگین»" and so a reader can tell
 * their own work from a copy they modified. It is deliberately *not* a foreign key: a preset the
 * app later renames or drops must not take the reader's edited copy with it.
 *
 * [inputs] is the reader's overrides, stored as `name=value` lines. A small denormalised blob rather
 * than a second table, because these are only ever read and written whole, with the script, and a
 * join for four numbers would buy nothing. Unparseable lines are skipped on read — a value stored
 * by a newer build must not stop an older one from opening the script.
 */
@Entity(tableName = "saved_scripts")
data class SavedScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,
    val presetId: String? = null,
    val inputs: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Dao
interface SavedScriptDao {
    /** Newest edit first: the thing a reader wants is almost always the thing they last touched. */
    @Query("SELECT * FROM saved_scripts ORDER BY updatedAtEpochMillis DESC")
    fun scripts(): Flow<List<SavedScriptEntity>>

    @Query("SELECT * FROM saved_scripts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): SavedScriptEntity?

    @Query("SELECT COUNT(*) FROM saved_scripts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(script: SavedScriptEntity): Long

    @Update
    suspend fun update(script: SavedScriptEntity)

    @Query("DELETE FROM saved_scripts WHERE id = :id")
    suspend fun delete(id: Long)
}
