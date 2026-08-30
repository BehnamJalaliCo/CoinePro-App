package com.coinepro.core.diagnostics

import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Where the log goes so that it is still there after the process is not.
 *
 * An interface rather than one class because two of the three callers are not a phone: a unit test
 * wants a sink it can inspect synchronously, and a build that has decided not to persist anything
 * passes null. The one real implementation is [FileLogSink].
 */
interface LogSink {

    /** Called on every accepted entry, from whatever thread wrote it. Must not block the caller. */
    fun append(entry: LogEntry)

    /** The newest [limit] entries the sink still holds, oldest first. */
    fun read(limit: Int): List<LogEntry>

    /** Deletes everything. This is the app's retention policy, exposed as a button. */
    fun clear()

    /** How much room the sink is currently taking, for the panel's storage row. */
    fun sizeBytes(): Long
}

/**
 * The log, on disk, in the app's private storage.
 *
 * ### Two files, and why not one
 *
 * Appending to a single file forever is the obvious implementation and it fails in the obvious way:
 * a phone left running for a week ends up holding a log nobody will ever read the start of, in a
 * directory the reader cannot see and did not agree to. Truncating it instead loses the history at
 * exactly the moment there is enough of it to be useful.
 *
 * So there are two: the active file, and the one before it. When the active file passes
 * [maxBytes] it is moved over the previous one and a new active file starts. That gives a floor —
 * a full file's worth of history is always retained, whatever happens next — and a ceiling of
 * twice [maxBytes] on disk, which is the number that matters to somebody whose phone is full.
 *
 * ### Off the caller's thread
 *
 * Entries are written on a single background thread. This matters more than it looks: the busiest
 * writer of this log is [RequestLogInterceptor], which runs on OkHttp's dispatcher, and the second
 * busiest is whatever composable just changed state on the main thread. A synchronous append would
 * put a file write in both paths, and a file write on the main thread is a dropped frame every
 * time the app logs anything.
 *
 * One thread rather than a pool, because the file is a sequence and two threads appending to a
 * sequence produce interleaved half-lines. It is a daemon thread: a background writer must never be
 * the reason the process stays alive.
 *
 * ### The format
 *
 * Tab-separated, one entry per line, fields in a fixed order. Not JSON — this module has no JSON
 * library, and adding one so that a log line can carry braces would be a dependency bought with
 * nothing. Tabs, newlines and backslashes inside a value are escaped, so a message containing a
 * newline cannot forge a second entry. A line that will not parse is skipped rather than throwing:
 * the last line of a file whose process died mid-write is routinely half a line, and a log that
 * refuses to load because of it is a log that is missing precisely when it was needed.
 */
class FileLogSink(
    private val directory: File,
    private val maxBytes: Long = MAX_BYTES,
    /**
     * Where appends run.
     *
     * Injected so a test can pass a direct executor and assert on the file immediately after the
     * call. Production takes the default, which is one daemon thread.
     */
    private val executor: Executor = defaultExecutor(),
) : LogSink {

    private val active = File(directory, ACTIVE_NAME)
    private val previous = File(directory, PREVIOUS_NAME)

    override fun append(entry: LogEntry) {
        val line = encode(entry)
        executor.execute {
            runCatching {
                directory.mkdirs()
                if (active.length() >= maxBytes) {
                    // `renameTo` over an existing file is what keeps this to two files rather than
                    // two plus a window where there are none.
                    previous.delete()
                    active.renameTo(previous)
                }
                active.appendText(line + "\n")
            }
        }
    }

    override fun read(limit: Int): List<LogEntry> = runCatching {
        val lines = readLines(previous) + readLines(active)
        lines.takeLast(limit).mapNotNull(::decode)
    }.getOrDefault(emptyList())

    override fun clear() {
        runCatching {
            active.delete()
            previous.delete()
        }
    }

    override fun sizeBytes(): Long = active.length() + previous.length()

    private fun readLines(file: File): List<String> =
        if (file.isFile) file.readLines() else emptyList()

    private companion object {
        const val ACTIVE_NAME = "app-log.tsv"
        const val PREVIOUS_NAME = "app-log.1.tsv"

        /**
         * Half a megabyte per file.
         *
         * A line here averages around a hundred and fifty bytes, so a full file is a few thousand
         * entries — hours of ordinary use, and far more than the six hundred the in-memory ring
         * holds. A megabyte of total footprint is invisible next to the app's own cache and is
         * still small enough to attach to a message.
         */
        const val MAX_BYTES: Long = 512L * 1024L

        fun defaultExecutor(): Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "coinepro-log").apply { isDaemon = true }
        }
    }
}

/* ------------------------------------------------------------------ the line format */

private const val SEPARATOR = '\t'
private const val FIELD_SEPARATOR = '\u001F'
private const val FIELD_COUNT = 9

internal fun encode(entry: LogEntry): String = listOf(
    entry.sequence.toString(),
    entry.epochMillis.toString(),
    entry.uptimeMillis.toString(),
    entry.level.name,
    entry.tag.name,
    escape(entry.thread),
    escape(entry.message),
    entry.fields.entries.joinToString(FIELD_SEPARATOR.toString()) { (key, value) ->
        escape(key) + "=" + escape(value)
    },
    escape(entry.error.orEmpty()),
).joinToString(SEPARATOR.toString())

/** Null for anything that does not parse, which the reader drops. See the class note on why. */
internal fun decode(line: String): LogEntry? {
    if (line.isBlank()) return null
    val parts = line.split(SEPARATOR)
    if (parts.size != FIELD_COUNT) return null
    val level = LogLevel.entries.firstOrNull { it.name == parts[3] } ?: return null
    // An unknown tag means the file was written by a build that had one this build does not. The
    // entry is still worth reading, so it is filed under the tag every log can express rather than
    // discarded — a downgrade should not lose history.
    val tag = LogTag.entries.firstOrNull { it.name == parts[4] } ?: LogTag.STATE
    return LogEntry(
        sequence = parts[0].toLongOrNull() ?: return null,
        epochMillis = parts[1].toLongOrNull() ?: return null,
        uptimeMillis = parts[2].toLongOrNull() ?: return null,
        level = level,
        tag = tag,
        message = unescape(parts[6]),
        fields = parts[7].takeIf(String::isNotEmpty)
            ?.split(FIELD_SEPARATOR)
            ?.mapNotNull { pair ->
                val at = pair.indexOf('=')
                if (at <= 0) null else unescape(pair.take(at)) to unescape(pair.substring(at + 1))
            }
            ?.toMap()
            .orEmpty(),
        error = parts[8].takeIf(String::isNotEmpty)?.let(::unescape),
        thread = unescape(parts[5]),
    )
}

/**
 * Backslash first, always.
 *
 * Escaping the separators before the escape character would turn `\` into `\\` after it had already
 * been used to encode a tab, and the value would come back with one too many on every round trip.
 */
private fun escape(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\t", "\\t")
    .replace("\n", "\\n")
    .replace("\u001F", "\\u")

private fun unescape(value: String): String {
    if (!value.contains('\\')) return value
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character != '\\' || index == value.lastIndex) {
            out.append(character)
            index++
            continue
        }
        when (value[index + 1]) {
            't' -> out.append('\t')
            'n' -> out.append('\n')
            'u' -> out.append('\u001F')
            '\\' -> out.append('\\')
            else -> out.append(value[index + 1])
        }
        index += 2
    }
    return out.toString()
}
