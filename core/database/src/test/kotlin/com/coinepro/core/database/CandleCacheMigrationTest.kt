package com.coinepro.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The version 5 to 6 migration, which renames the candle cache's key column and must not lose a row.
 *
 * ### Why this runs the statements rather than opening a database
 *
 * Room's own `MigrationTestHelper` needs an instrumented device or an emulator, and `core:database`
 * has neither on its unit-test classpath. So [MIGRATION_5_6] is written as an ordered list of
 * statements — [CANDLE_CACHE_INTERVAL_REBUILD] — and this file runs that list against [TinySql], a
 * deliberately tiny executor that understands nothing beyond the handful of statement shapes the
 * migration uses and **throws on anything else**. It cannot silently pass a statement it does not
 * understand, which is the only property that makes a stand-in like this worth having.
 *
 * What that buys is the thing worth guarding: a row written under version 5 is seeded, the real
 * migration statements run over it, and the row is read back out of the version 6 table with every
 * value where it belongs. The failure this catches is the expensive one — an edit that replaces the
 * rebuild with a drop, or that reorders the columns in the `INSERT … SELECT` so a volume lands in a
 * price. Dropping the cache is not a cosmetic regression: it puts every chart in the app back behind
 * an empty rectangle on the first open after an update.
 */
class CandleCacheMigrationTest {

    /** The version 5 table, exactly as `MIGRATION_4_5` created it. */
    private val version5Table = """
        CREATE TABLE IF NOT EXISTS cached_candles (
            symbol TEXT NOT NULL,
            timeframe TEXT NOT NULL,
            t INTEGER NOT NULL,
            o REAL NOT NULL,
            h REAL NOT NULL,
            l REAL NOT NULL,
            c REAL NOT NULL,
            v REAL NOT NULL,
            cachedAtEpochMillis INTEGER NOT NULL,
            PRIMARY KEY (symbol, timeframe, t)
        )
    """.trimIndent()

    private fun seededVersion5(): TinySql {
        val db = TinySql()
        db.exec(version5Table)
        db.exec(
            "INSERT INTO cached_candles (symbol, timeframe, t, o, h, l, c, v, cachedAtEpochMillis) " +
                "VALUES ('XAUUSD', 'H4', 1700000000, 2500.5, 2510.25, 2495.75, 2508.0, 1234.5, 1700000123456)",
        )
        db.exec(
            "INSERT INTO cached_candles (symbol, timeframe, t, o, h, l, c, v, cachedAtEpochMillis) " +
                "VALUES ('BTCUSDT', 'M5', 1700000300, 42000.0, 42100.0, 41900.0, 42050.0, 12.25, 1700000123456)",
        )
        return db
    }

    private fun migrate(db: TinySql) = CANDLE_CACHE_INTERVAL_REBUILD.forEach(db::exec)

    @Test
    fun `a row written under version five survives the migration with every value intact`() {
        val db = seededVersion5()

        migrate(db)

        val rows = db.rows("cached_candles")
        assertEquals(2, rows.size)
        val gold = rows.single { it["symbol"] == "XAUUSD" }
        assertEquals("H4", gold["interval"])
        assertEquals(1_700_000_000L, gold["t"])
        assertEquals(2500.5, gold["o"] as Double, 1e-9)
        assertEquals(2510.25, gold["h"] as Double, 1e-9)
        assertEquals(2495.75, gold["l"] as Double, 1e-9)
        assertEquals(2508.0, gold["c"] as Double, 1e-9)
        assertEquals(1234.5, gold["v"] as Double, 1e-9)
        assertEquals(1_700_000_123_456L, gold["cachedAtEpochMillis"])
    }

    @Test
    fun `the timeframe column becomes the interval column and nothing is left of the old name`() {
        val db = seededVersion5()

        migrate(db)

        val columns = db.columns("cached_candles")
        assertTrue(columns.contains("interval"))
        assertFalse(columns.contains("timeframe"))
        // The rest of the row is untouched, so a chart reading a migrated series gets the same
        // numbers it cached.
        assertEquals(
            listOf("symbol", "interval", "t", "o", "h", "l", "c", "v", "cachedAtEpochMillis"),
            columns,
        )
    }

    @Test
    fun `every preset keeps its key, because an enum name and a wire spelling are the same text`() {
        // This is the whole reason the migration can copy rather than translate. If it ever stops
        // being true, a migrated row would point at a series nothing looks up, and the reader's
        // cache would be present but unreachable — which looks exactly like having dropped it.
        val db = TinySql()
        db.exec(version5Table)
        val presets = listOf("M1", "M2", "M3", "M5", "M10", "M15", "M30", "M45", "H1", "H2", "H3", "H4", "D1", "W1", "MN1")
        for ((index, preset) in presets.withIndex()) {
            db.exec(
                "INSERT INTO cached_candles (symbol, timeframe, t, o, h, l, c, v, cachedAtEpochMillis) " +
                    "VALUES ('BTCUSDT', '$preset', ${1_700_000_000L + index}, 1.0, 2.0, 0.5, 1.5, 1.0, 1700000123456)",
            )
        }

        migrate(db)

        assertEquals(presets.toSet(), db.rows("cached_candles").map { it["interval"] }.toSet())
    }

    @Test
    fun `a custom interval and a preset of the same length are two rows after the migration`() {
        // The point of the whole change. Under the old key a custom interval had to borrow a
        // preset's enum name; under the new one `5` and `M5` are different strings and cannot
        // overwrite each other.
        val db = seededVersion5()
        migrate(db)

        db.exec(
            "INSERT INTO cached_candles (symbol, `interval`, t, o, h, l, c, v, cachedAtEpochMillis) " +
                "VALUES ('BTCUSDT', '5', 1700000300, 1.0, 2.0, 0.5, 1.5, 1.0, 1700000123456)",
        )
        db.exec(
            "INSERT INTO cached_candles (symbol, `interval`, t, o, h, l, c, v, cachedAtEpochMillis) " +
                "VALUES ('BTCUSDT', '205', 1700000300, 9.0, 9.0, 9.0, 9.0, 9.0, 1700000123456)",
        )

        val btc = db.rows("cached_candles").filter { it["symbol"] == "BTCUSDT" && it["t"] == 1_700_000_300L }
        assertEquals(setOf("M5", "5", "205"), btc.map { it["interval"] }.toSet())
        assertEquals(42_000.0, btc.single { it["interval"] == "M5" }["o"] as Double, 1e-9)
    }

    @Test
    fun `the new table carries the index Room will look for`() {
        val db = seededVersion5()

        migrate(db)

        assertEquals(
            mapOf("index_cached_candles_symbol_interval_t" to listOf("symbol", "interval", "t")),
            db.indexNames(),
        )
    }

    @Test
    fun `the rows are copied before the old table is dropped, and nothing is deleted`() {
        // A structural guard on top of the round trip above. The one-word edit that turns this
        // migration into a data loss is moving the drop ahead of the copy, and it would still pass
        // a test that only checked the final schema.
        val statements = CANDLE_CACHE_INTERVAL_REBUILD.map { it.replace(Regex("\\s+"), " ").trim() }
        val copy = statements.indexOfFirst { it.startsWith("INSERT") && it.contains("SELECT") }
        val drop = statements.indexOfFirst { it.startsWith("DROP TABLE") }
        assertTrue("the migration must copy the rows", copy >= 0)
        assertTrue("the migration must drop the old table", drop >= 0)
        assertTrue("the copy has to happen before the drop", copy < drop)
        assertTrue(statements.none { it.startsWith("DELETE") })
    }

    @Test
    fun `the migration is registered for the version step the database actually takes`() {
        assertEquals(5, MIGRATION_5_6.startVersion)
        assertEquals(6, MIGRATION_5_6.endVersion)
    }
}

/**
 * A very small SQL executor, for migration statements only.
 *
 * It supports `CREATE TABLE`, `INSERT … VALUES`, `INSERT … SELECT`, `DROP TABLE`, `ALTER TABLE …
 * RENAME TO` and `CREATE INDEX`, honours a primary key by replacing on conflict, and throws
 * [IllegalArgumentException] on every other statement. Throwing is the important half: a stand-in
 * that quietly ignored what it did not understand would report a migration as passing precisely
 * when it had grown a statement nobody had thought about.
 */
private class TinySql {

    private class Table(val columns: MutableList<String>, val primaryKey: List<String>) {
        val rows = mutableListOf<MutableMap<String, Any?>>()
    }

    private val tables = LinkedHashMap<String, Table>()
    private val indices = LinkedHashMap<String, List<String>>()

    fun columns(table: String): List<String> = table(table).columns.toList()

    fun rows(table: String): List<Map<String, Any?>> = table(table).rows.map { it.toMap() }

    fun indexNames(): Map<String, List<String>> = indices.toMap()

    fun exec(rawSql: String) {
        val sql = rawSql.replace("`", "").replace(Regex("\\s+"), " ").trim().trimEnd(';')
        when {
            sql.startsWith("CREATE TABLE", ignoreCase = true) -> createTable(sql)
            sql.startsWith("CREATE INDEX", ignoreCase = true) -> createIndex(sql)
            sql.startsWith("INSERT", ignoreCase = true) && sql.contains(" VALUES ", ignoreCase = true) ->
                insertValues(sql)
            sql.startsWith("INSERT", ignoreCase = true) && sql.contains(" SELECT ", ignoreCase = true) ->
                insertSelect(sql)
            sql.startsWith("DROP TABLE", ignoreCase = true) -> dropTable(sql)
            sql.startsWith("ALTER TABLE", ignoreCase = true) -> renameTable(sql)
            else -> throw IllegalArgumentException("Unsupported statement in a migration test: $sql")
        }
    }

    private fun table(name: String): Table =
        tables[name] ?: throw IllegalArgumentException("No such table: $name")

    private fun createTable(sql: String) {
        val match = Regex(
            "^CREATE TABLE (?:IF NOT EXISTS )?(\\w+) \\((.*)\\)$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(sql) ?: throw IllegalArgumentException("Unparsed CREATE TABLE: $sql")
        val name = match.groupValues[1]
        if (tables.containsKey(name)) return
        val columns = mutableListOf<String>()
        var primaryKey = emptyList<String>()
        for (part in splitTopLevel(match.groupValues[2])) {
            if (part.startsWith("PRIMARY KEY", ignoreCase = true)) {
                primaryKey = part.substringAfter('(').substringBeforeLast(')')
                    .split(',').map { it.trim() }
            } else {
                columns += part.substringBefore(' ')
            }
        }
        tables[name] = Table(columns, primaryKey)
    }

    private fun createIndex(sql: String) {
        val match = Regex(
            "^CREATE INDEX (?:IF NOT EXISTS )?(\\w+) ON (\\w+) \\((.*)\\)$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(sql) ?: throw IllegalArgumentException("Unparsed CREATE INDEX: $sql")
        table(match.groupValues[2])
        indices[match.groupValues[1]] = match.groupValues[3].split(',').map { it.trim() }
    }

    private fun insertValues(sql: String) {
        val match = Regex(
            "^INSERT (?:OR REPLACE )?INTO (\\w+) \\((.*?)\\) VALUES \\((.*)\\)$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(sql) ?: throw IllegalArgumentException("Unparsed INSERT: $sql")
        val target = table(match.groupValues[1])
        val columns = match.groupValues[2].split(',').map { it.trim() }
        val values = splitTopLevel(match.groupValues[3]).map(::literal)
        require(columns.size == values.size) { "Column and value counts differ: $sql" }
        put(target, columns.zip(values).toMap())
    }

    private fun insertSelect(sql: String) {
        val match = Regex(
            "^INSERT (?:OR REPLACE )?INTO (\\w+) \\((.*?)\\) SELECT (.*) FROM (\\w+)$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(sql) ?: throw IllegalArgumentException("Unparsed INSERT … SELECT: $sql")
        val target = table(match.groupValues[1])
        val into = match.groupValues[2].split(',').map { it.trim() }
        val from = match.groupValues[3].split(',').map { it.trim() }
        val source = table(match.groupValues[4])
        require(into.size == from.size) { "Column counts differ across the copy: $sql" }
        for (column in from) {
            require(source.columns.contains(column)) { "No such column to copy: $column" }
        }
        for (row in source.rows.toList()) {
            put(target, into.indices.associate { into[it] to row[from[it]] })
        }
    }

    private fun dropTable(sql: String) {
        val name = sql.removePrefix("DROP TABLE ").removePrefix("IF EXISTS ").trim()
        tables.remove(name)
    }

    private fun renameTable(sql: String) {
        val match = Regex("^ALTER TABLE (\\w+) RENAME TO (\\w+)$", RegexOption.IGNORE_CASE)
            .matchEntire(sql) ?: throw IllegalArgumentException("Unparsed ALTER TABLE: $sql")
        val existing = tables.remove(match.groupValues[1])
            ?: throw IllegalArgumentException("No such table: ${match.groupValues[1]}")
        tables[match.groupValues[2]] = existing
    }

    private fun put(target: Table, row: Map<String, Any?>) {
        for (column in row.keys) {
            require(target.columns.contains(column)) { "No such column: $column" }
        }
        val existing = target.rows.firstOrNull { held ->
            target.primaryKey.isNotEmpty() && target.primaryKey.all { held[it] == row[it] }
        }
        if (existing != null) target.rows.remove(existing)
        target.rows += row.toMutableMap()
    }

    private fun literal(token: String): Any? {
        val text = token.trim()
        return when {
            text.equals("NULL", ignoreCase = true) -> null
            text.startsWith("'") && text.endsWith("'") -> text.substring(1, text.length - 1)
            text.contains('.') -> text.toDouble()
            else -> text.toLong()
        }
    }

    /** Splits on commas that are not inside brackets, which is what a `PRIMARY KEY (…)` clause needs. */
    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (character in text) {
            when {
                character == '(' -> { depth++; current.append(character) }
                character == ')' -> { depth--; current.append(character) }
                character == ',' && depth == 0 -> { parts += current.toString().trim(); current.clear() }
                else -> current.append(character)
            }
        }
        if (current.isNotBlank()) parts += current.toString().trim()
        return parts
    }
}
