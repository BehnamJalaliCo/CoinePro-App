package com.coinepro.core.database

/**
 * A very small SQL executor, for migration statements only.
 *
 * It supports `CREATE TABLE`, `INSERT … VALUES`, `INSERT … SELECT`, `DROP TABLE`, `ALTER TABLE …
 * RENAME TO`, `ALTER TABLE … ADD COLUMN … DEFAULT …` and `CREATE INDEX`, honours a primary key by replacing on conflict, and throws
 * [IllegalArgumentException] on every other statement. Throwing is the important half: a stand-in
 * that quietly ignored what it did not understand would report a migration as passing precisely
 * when it had grown a statement nobody had thought about.
 */
internal class TinySql {

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
            sql.startsWith("ALTER TABLE", ignoreCase = true) && sql.contains(" ADD COLUMN", ignoreCase = true) ->
                addColumn(sql)
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

    /**
     * `ALTER TABLE x ADD COLUMN y TYPE NOT NULL DEFAULT z`.
     *
     * The default is applied to the rows that are already there, which is the entire behaviour the
     * 6→7 migration relies on: a script saved before 4.82.0 has to come back complete, not with
     * six holes in it.
     */
    private fun addColumn(sql: String) {
        val match = Regex(
            "^ALTER TABLE (\\w+) ADD COLUMN (\\w+) (\\w+)(?: NOT NULL)?(?: DEFAULT (.+))?$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(sql) ?: throw IllegalArgumentException("Unparsed ALTER TABLE ADD COLUMN: $sql")
        val target = table(match.groupValues[1])
        val column = match.groupValues[2]
        if (column in target.columns) throw IllegalArgumentException("Duplicate column: $column")
        val default: Any? = match.groupValues[4].takeIf { it.isNotEmpty() }?.let { literal(it.trim()) }
        target.columns += column
        for (row in target.rows) row[column] = default
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
