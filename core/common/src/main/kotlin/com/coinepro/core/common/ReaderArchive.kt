package com.coinepro.core.common

/**
 * **Everything the reader made** — one file (run Σ, S6; doctrine D8).
 *
 * ### Why this exists
 *
 * Because a watchlist, a chart layout, a script and a journal entry are the only things in this app
 * that cannot be refetched. Every quote, every candle and every signal comes back from a server on
 * request; these came out of a person, and a new phone without them is a new install.
 *
 * D8's rule is «watchlists, layouts, my scripts, the journal and the streak all sync and export»,
 * and its gate is a round trip per store: write, export, wipe, import, compare. That gate is
 * `ReaderArchiveTest`, and this is the format it runs against.
 *
 * ### Why the format is written out rather than serialised
 *
 * Two reasons, and the second is the one that decided it. The first is that this module has no JSON
 * library and adding one for a file this shape would be a dependency for nothing. The second is
 * that **an archive is opened by the person who made it** — it goes in a note, a message, a backup
 * folder — and the first thing they should see is their own watchlist, not a wrapper.
 *
 * ### Why the payloads are length-prefixed
 *
 * Because a script and a journal entry are free text: quotes, newlines, Persian, and — as
 * `ScriptHistoryTest` showed the hard way — the occasional control character somebody typed into a
 * label. A separator-based format has to either escape the payload or forbid something, and both
 * eventually lose somebody a record. Here the parser is told how many characters to take.
 *
 * Pure Kotlin, so the same archive is written and read by the web build.
 */
data class ReaderArchive(
    /** The format's version, so a later reader knows what it has. */
    val version: Int = VERSION,
    val writtenAtEpochMillis: Long = 0L,
    /** Symbols the reader is watching, in their order. */
    val watchlist: List<String> = emptyList(),
    /** Chart layouts, each a name and the blob the layout store already writes. */
    val layouts: List<ArchivedRecord> = emptyList(),
    /** The reader's own scripts, each a `.nama` file. */
    val scripts: List<ArchivedRecord> = emptyList(),
    /** Journal entries, each the blob the journal already stores. */
    val journal: List<ArchivedRecord> = emptyList(),
    /** The arena streak: what is on it now and the best it has been. */
    val streak: ArchivedStreak? = null,
) {

    /** Whether there is anything in here worth writing to a file. */
    val isEmpty: Boolean
        get() = watchlist.isEmpty() && layouts.isEmpty() && scripts.isEmpty() &&
            journal.isEmpty() && streak == null

    /** How many things the archive holds, for «۴۲ chose» rather than a byte count. */
    val count: Int get() = watchlist.size + layouts.size + scripts.size + journal.size

    companion object {
        const val VERSION: Int = 1

        /** The extension the file takes. */
        const val EXTENSION: String = "prochart"
    }
}

/**
 * One thing the reader made: a name they gave it and the text that is it.
 *
 * A name and a body rather than a typed record per store, because this file must not become a
 * second definition of what a layout is. The stores own their own shapes; the archive carries them
 * through unread, which is also why a store can change its blob without this format moving.
 */
data class ArchivedRecord(val name: String, val body: String)

/** The streak, which is two numbers and the day they were last true of. */
data class ArchivedStreak(val current: Int, val best: Int, val lastDayEpochDay: Long)

/**
 * The archive as text, and back.
 *
 * ```
 * # prochart-archive 1
 * # written 1700000000000
 * [watchlist]
 * BTCUSDT
 * XAUUSD
 * [scripts]
 * 12:میانگین من:214:length = input(20, …
 * [streak]
 * 3 7 19700
 * ```
 *
 * A section header is a line in brackets; a record is `nameLength:name:bodyLength:body` with no
 * separator after it, so the next record begins exactly where this one ended.
 */
object ReaderArchiveFile {

    private const val HEADER = "# prochart-archive"
    private const val WRITTEN = "# written"

    private const val WATCHLIST = "[watchlist]"
    private const val LAYOUTS = "[layouts]"
    private const val SCRIPTS = "[scripts]"
    private const val JOURNAL = "[journal]"
    private const val STREAK = "[streak]"

    fun write(archive: ReaderArchive): String = buildString {
        appendLine("$HEADER ${archive.version}")
        appendLine("$WRITTEN ${archive.writtenAtEpochMillis}")
        if (archive.watchlist.isNotEmpty()) {
            appendLine(WATCHLIST)
            // One symbol a line and no length prefix: a ticker is letters, digits and a slash, and
            // the one place in this file a person might want to edit by hand is this list.
            for (symbol in archive.watchlist) appendLine(symbol.trim())
        }
        section(LAYOUTS, archive.layouts)
        section(SCRIPTS, archive.scripts)
        section(JOURNAL, archive.journal)
        archive.streak?.let {
            appendLine(STREAK)
            appendLine("${it.current} ${it.best} ${it.lastDayEpochDay}")
        }
    }

    private fun StringBuilder.section(header: String, records: List<ArchivedRecord>) {
        if (records.isEmpty()) return
        appendLine(header)
        for (record in records) {
            val name = record.name.replace('\n', ' ')
            append(name.length).append(':').append(name)
                .append(':').append(record.body.length).append(':').append(record.body)
            appendLine()
        }
    }

    /**
     * Reads an archive, or null when the text is not one.
     *
     * Null rather than a half-read archive: importing half of somebody's watchlist over the top of
     * the one they have is worse than importing none of it, because they cannot tell which half.
     */
    fun read(text: String): ReaderArchive? {
        // **A position scanner, not a line loop.**
        //
        // The first version of this split the text into lines, which reads beautifully and is
        // wrong: a record's body is length-prefixed *because* it contains newlines — a script, a
        // journal entry — and a line loop cuts every one of them in half. The tests caught it on
        // the first run, which is the only reason this comment is here rather than a bug report.
        //
        // So headers and the simple sections are read a line at a time, and a record is consumed
        // from the character after its section header to the character its own length names.
        if (!text.startsWith("$HEADER ")) return null
        var cursor = text.indexOf('\n').takeIf { it >= 0 } ?: text.length
        val version = text.substring(HEADER.length, cursor).trim().toIntOrNull() ?: return null
        if (cursor < text.length) cursor++

        var writtenAt = 0L
        val watchlist = mutableListOf<String>()
        val layouts = mutableListOf<ArchivedRecord>()
        val scripts = mutableListOf<ArchivedRecord>()
        val journal = mutableListOf<ArchivedRecord>()
        var streak: ArchivedStreak? = null
        var section = ""

        while (cursor < text.length) {
            val recordsHere = when (section) {
                LAYOUTS -> layouts
                SCRIPTS -> scripts
                JOURNAL -> journal
                else -> null
            }
            if (recordsHere != null && text[cursor] != '[') {
                val record = recordAt(text, cursor)
                if (record != null) {
                    recordsHere += record.first
                    cursor = record.second
                    if (cursor < text.length && text[cursor] == '\n') cursor++
                    continue
                }
                // Not a record: fall through and read it as a line, which is how a section written
                // by a later version — one this build has no idea about — is skipped rather than
                // taking the rest of the file with it.
            }
            val lineEnd = text.indexOf('\n', cursor).takeIf { it >= 0 } ?: text.length
            val line = text.substring(cursor, lineEnd).trim()
            cursor = if (lineEnd < text.length) lineEnd + 1 else text.length
            when {
                line.isEmpty() -> Unit
                line.startsWith("$WRITTEN ") -> writtenAt = line.removePrefix("$WRITTEN ").trim().toLongOrNull() ?: 0L
                line.startsWith("#") -> Unit
                line.startsWith("[") -> section = line
                section == WATCHLIST -> watchlist += line
                section == STREAK -> streak = streakOf(line) ?: streak
            }
        }
        return ReaderArchive(
            version = version,
            writtenAtEpochMillis = writtenAt,
            watchlist = watchlist,
            layouts = layouts,
            scripts = scripts,
            journal = journal,
            streak = streak,
        )
    }

    /**
     * One `nameLength:name:bodyLength:body` record starting at [start], and where it ended.
     *
     * Null when the lengths do not add up, and the caller then treats the line as an unknown one
     * rather than as a broken record. A record read *wrong* would be a script with its last line
     * missing, which compiles about half the time — and a script that runs and draws something
     * slightly different is worse than one that refuses.
     */
    private fun recordAt(text: String, start: Int): Pair<ArchivedRecord, Int>? {
        val firstColon = text.indexOf(':', start).takeIf { it > start } ?: return null
        val nameLength = text.substring(start, firstColon).toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val nameStart = firstColon + 1
        val nameEnd = nameStart + nameLength
        if (nameEnd >= text.length || text[nameEnd] != ':') return null
        val bodyLengthStart = nameEnd + 1
        val secondColon = text.indexOf(':', bodyLengthStart).takeIf { it > bodyLengthStart } ?: return null
        val bodyLength = text.substring(bodyLengthStart, secondColon).toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val bodyStart = secondColon + 1
        val bodyEnd = bodyStart + bodyLength
        if (bodyEnd > text.length) return null
        val record = ArchivedRecord(
            name = text.substring(nameStart, nameEnd),
            body = text.substring(bodyStart, bodyEnd),
        )
        return record to bodyEnd
    }

    private fun streakOf(line: String): ArchivedStreak? {
        val parts = line.split(' ').mapNotNull { it.toLongOrNull() }
        if (parts.size < 3) return null
        return ArchivedStreak(
            current = parts[0].toInt().coerceAtLeast(0),
            best = parts[1].toInt().coerceAtLeast(0),
            lastDayEpochDay = parts[2],
        )
    }
}

/**
 * How an imported archive meets what is already there.
 *
 * Not a question this format answers by itself, and the reason it is an enum rather than a boolean
 * is that the three answers are genuinely different and the reader has to choose:
 *
 * * [MERGE] — add what is missing and leave everything else. The safe one, and the default: a
 *   reader restoring a backup onto a phone they have been using does not want their last week gone.
 * * [REPLACE] — the archive wins. What a new phone wants.
 * * [MINE] — keep what is here and take only what has no counterpart. What a reader importing
 *   somebody *else's* archive wants: their lists, not a stranger's.
 */
enum class ImportRule { MERGE, REPLACE, MINE }

/**
 * Merges [incoming] into [existing] by [rule], with no side effects.
 *
 * Pure, so the rule can be tested without a database, and so the web build can apply the same one.
 * Order is preserved and duplicates are not created: a watchlist that gains a symbol it already had
 * gains nothing, which is what stops a reader importing the same backup twice and getting two of
 * everything.
 */
object ArchiveMerge {

    fun symbols(existing: List<String>, incoming: List<String>, rule: ImportRule): List<String> =
        when (rule) {
            ImportRule.REPLACE -> incoming.distinct()
            ImportRule.MERGE -> (existing + incoming).distinct()
            ImportRule.MINE -> (existing + incoming.filterNot { it in existing }).distinct()
        }

    /**
     * Records by name. [MERGE] lets the incoming copy win a name clash and [MINE] keeps the
     * reader's — which is the only difference between them, and the whole reason both exist.
     */
    fun records(
        existing: List<ArchivedRecord>,
        incoming: List<ArchivedRecord>,
        rule: ImportRule,
    ): List<ArchivedRecord> = when (rule) {
        ImportRule.REPLACE -> incoming
        ImportRule.MERGE -> {
            val byName = existing.associateBy { it.name }.toMutableMap()
            for (record in incoming) byName[record.name] = record
            (existing.map { byName.getValue(it.name) } + incoming.filterNot { record -> existing.any { it.name == record.name } })
        }
        ImportRule.MINE ->
            existing + incoming.filterNot { record -> existing.any { it.name == record.name } }
    }

    /** The better of two streaks: the longer current run, and the better best either has seen. */
    fun streak(existing: ArchivedStreak?, incoming: ArchivedStreak?, rule: ImportRule): ArchivedStreak? =
        when {
            rule == ImportRule.REPLACE -> incoming ?: existing
            existing == null -> incoming
            incoming == null -> existing
            // Never the smaller of the two. A streak is a record of something the reader did, and
            // an import that shortened it would be the app telling them it did not happen.
            else -> ArchivedStreak(
                current = maxOf(existing.current, incoming.current),
                best = maxOf(existing.best, incoming.best),
                lastDayEpochDay = maxOf(existing.lastDayEpochDay, incoming.lastDayEpochDay),
            )
        }
}
