package com.coinepro.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The version 6 to 7 migration: a saved script grows six columns and loses nothing (4.82.0).
 *
 * This table is one of the three in the database that cannot be refetched — it is the reader's own
 * code — so the migration is written out as statements and run here against [TinySql] rather than
 * trusted. The failure being guarded is not exotic: a column added without a default leaves every
 * existing row with a hole in it, and Room then refuses to open the database at all.
 */
class SavedScriptMigrationTest {

    /** The version 6 table, exactly as `SavedScriptEntity` was before 4.82.0. */
    private val version6Table = """
        CREATE TABLE IF NOT EXISTS saved_scripts (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            source TEXT NOT NULL,
            presetId TEXT,
            inputs TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL
        )
    """.trimIndent()

    private fun seeded(): TinySql {
        val db = TinySql()
        db.exec(version6Table)
        db.exec(
            "INSERT INTO saved_scripts (id, name, source, presetId, inputs, createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES (1, 'میانگین من', 'plot(ta.ema(close, 20))', 'ema-cross', 'دوره=20.0', 1700000000000, 1700000100000)",
        )
        db.exec(
            "INSERT INTO saved_scripts (id, name, source, presetId, inputs, createdAtEpochMillis, updatedAtEpochMillis) " +
                "VALUES (2, 'RSI', 'plot(ta.rsi(close, 14))', NULL, '', 1700000000000, 1700000200000)",
        )
        return db
    }

    private fun migrate(db: TinySql) = SAVED_SCRIPT_COLUMNS.forEach(db::exec)

    @Test
    fun `a script saved before the new columns keeps everything it had`() {
        val db = seeded()

        migrate(db)

        val rows = db.rows("saved_scripts")
        assertEquals(2, rows.size)
        val mine = rows.single { it["id"] == 1L }
        assertEquals("میانگین من", mine["name"])
        assertEquals("plot(ta.ema(close, 20))", mine["source"])
        assertEquals("ema-cross", mine["presetId"])
        assertEquals("دوره=20.0", mine["inputs"])
        assertEquals(1700000100000L, mine["updatedAtEpochMillis"])
    }

    @Test
    fun `and arrives with every new column filled rather than null`() {
        // The whole reason each statement carries a DEFAULT. A NOT NULL column added without one
        // leaves existing rows unreadable, and Room refuses to open a database it cannot map.
        val db = seeded()

        migrate(db)

        val mine = db.rows("saved_scripts").single { it["id"] == 1L }
        assertEquals("", mine["description"])
        assertEquals(DEFAULT_SCRIPT_COLOUR, mine["colour"])
        assertEquals("", mine["tags"])
        assertEquals(0L, mine["ownPane"])
        assertEquals("", mine["publicId"])
        assertEquals("", mine["history"])
    }

    @Test
    fun `the colour default is the one the entity declares`() {
        // Written as decimal in the SQL and as hex in Kotlin, which is exactly the kind of pair
        // that drifts. A script that came back a different gold than the app's would look broken
        // for a reason nobody would find by reading either file alone.
        val statement = SAVED_SCRIPT_COLUMNS.single { "colour" in it }
        assertTrue(statement, "DEFAULT $DEFAULT_SCRIPT_COLOUR" in statement)
    }

    @Test
    fun `every new column is added and none is a rebuild`() {
        // A rebuild would mean a copy, and a copy is where rows get lost. Six ALTERs and nothing
        // else is the property worth pinning, because the cheap edit that breaks it — dropping and
        // recreating the table — looks perfectly reasonable in a diff.
        assertEquals(6, SAVED_SCRIPT_COLUMNS.size)
        for (statement in SAVED_SCRIPT_COLUMNS) {
            assertTrue(statement, statement.startsWith("ALTER TABLE saved_scripts ADD COLUMN "))
            assertTrue("no default: $statement", " DEFAULT " in statement)
            assertTrue("nullable: $statement", " NOT NULL " in statement)
        }
        assertTrue(SAVED_SCRIPT_COLUMNS.none { "DROP" in it || "SELECT" in it })
    }

    @Test
    fun `the migration is registered for the version step the database actually takes`() {
        assertEquals(6, MIGRATION_6_7.startVersion)
        assertEquals(7, MIGRATION_6_7.endVersion)
    }
}
