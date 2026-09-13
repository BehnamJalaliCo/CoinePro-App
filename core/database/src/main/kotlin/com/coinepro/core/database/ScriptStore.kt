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
    /**
     * The reader's own description, colour, tags and pane (4.82.0, run Σ item S3 C).
     *
     * Five columns rather than a second table, for the same reason [inputs] is a blob: they are
     * only ever read and written with the script, and a join for a colour would buy nothing.
     *
     * Every one has a default, which is what lets the migration below add them with a plain
     * `ALTER TABLE` and lets an older row — one saved before 4.82.0 — be read without ceremony. A
     * script with no colour is not broken; it is a script whose owner has not chosen one.
     */
    val description: String = "",
    val colour: Long = DEFAULT_SCRIPT_COLOUR,
    /** Comma-separated, as the reader typed them. */
    val tags: String = "",
    val ownPane: Boolean = false,
    /**
     * The public id a share link addresses, or empty for a script never shared.
     *
     * Kept beside the row id rather than replacing it: the row id is this device's and the public
     * id is the world's, and conflating them would mean a script's address changing when it is
     * restored onto a new phone.
     */
    val publicId: String = "",
    /**
     * Earlier revisions, newest first, at most five — `at:length:source` records, end to end.
     *
     * Length-prefixed rather than separated, because a revision is a *whole script*: brackets,
     * quotes, newlines, Persian and — as `ScriptHistoryTest` shows — even a control character a
     * reader put inside a string literal. An encoding with a delimiter has to either escape its
     * payload or forbid something, and both eventually lose somebody a version. Nothing is
     * forbidden here: the parser is told how many characters to take. See `ScriptController`.
     */
    val history: String = "",
)

/** The app's gold. Mirrors `ScriptDocument.DEFAULT_COLOUR`, which this module cannot see. */
const val DEFAULT_SCRIPT_COLOUR: Long = 0xFFD8A848

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
