package com.coinepro.core.diagnostics

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How loud one entry is. Ordered, so a filter can say "warnings and worse". */
enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

/**
 * Where an entry came from.
 *
 * A fixed set rather than free strings, because the point of a tag is filtering and free strings
 * drift — `"net"`, `"network"` and `"Network"` are three tags nobody can filter on. Adding a domain
 * here is deliberate; misspelling one is impossible.
 */
enum class LogTag {
    /** App start, foreground, background, process death. */
    LIFECYCLE,

    /** Navigation: which destination, from where. */
    NAVIGATION,

    /** HTTP. The detail lives in [RequestLog]; this is the narrative around it. */
    NETWORK,

    /** The realtime price socket: connect, drop, reconnect, backoff. */
    SOCKET,

    /** Sign-in, refresh, session adoption, sign-out. */
    AUTH,

    /**
     * Which backend the app is talking to, and when that changed.
     *
     * Its own tag rather than a field on [AUTH], because the two servers are separate accounts and
     * the single most confusing class of report — "it works and then it says I am signed out" — is
     * almost always a platform switch nobody saw happen.
     */
    PLATFORM,

    /**
     * A gateway translating a server's answer into the app's model.
     *
     * Separate from [NETWORK] because these fail differently: the call succeeded, the status was
     * 200, and the body did not have the shape the app expected. A 200 that produced nothing is
     * invisible in a request table and obvious in a log.
     */
    GATEWAY,

    /** A controller's state moving — loading, loaded, failed. */
    STATE,

    /** The chart: series loads, viewport, tools, drawings. */
    CHART,

    /** Push and local alerts: received, filtered, shown, tapped. */
    NOTIFICATION,

    /** Reads and writes to DataStore and Room. */
    STORAGE,

    /**
     * The app's own gates: the lock screen, the admin door, an integrity check.
     *
     * An operator investigating "somebody got into the panel" needs the attempts in the same
     * timeline as everything else, and a failed unlock that was never recorded is one nobody can
     * investigate at all.
     */
    SECURITY,

    /** Anything measured: a duration, a count, a frame. */
    PERFORMANCE,
}

/**
 * One line in the log.
 *
 * [fields] is a map rather than a formatted string on purpose. A message that has already been
 * flattened into prose cannot be filtered, grouped or counted, and the first thing anybody wants to
 * do with a log is filter it — "every SOCKET entry where `platform` is TRADEYAR". The message says
 * what happened; the fields say what it happened to.
 *
 * Both clocks are kept, and they answer different questions. [epochMillis] is the wall clock and it
 * is what lines an entry up against a server's own log, which is the whole point of exporting one.
 * [uptimeMillis] is monotonic and it is what survives the phone's clock being wrong, a timezone
 * change mid-session, or an NTP correction landing in the middle of the minute being investigated —
 * a wall clock that jumps backwards makes a sequence of events look impossible.
 */
data class LogEntry(
    val sequence: Long,
    val epochMillis: Long,
    val uptimeMillis: Long,
    val level: LogLevel,
    val tag: LogTag,
    val message: String,
    val fields: Map<String, String> = emptyMap(),
    /** Present only on ERROR, and only ever the class name plus message — never a live throwable. */
    val error: String? = null,
    /** The thread that wrote it: the difference between a main-thread stall and a background one. */
    val thread: String = "",
) {
    /**
     * One line, for the export, the clipboard and the crash report.
     *
     * UTC and ISO-8601, deliberately, and both halves of that are a decision. UTC because the
     * developer reading this is comparing it against a server log that is also UTC, and an offset
     * they have to apply by hand is an offset they will get wrong once. ISO-8601 because it sorts
     * lexicographically, which means `sort` and `grep` work on the file without a parser.
     *
     * The rendering carries no locale and no Persian digits. This string is not read by a reader of
     * the app; it is read by whoever is fixing it, in a terminal.
     */
    fun render(): String = buildString {
        append(ISO.format(Instant.ofEpochMilli(epochMillis)))
        append(' ')
        append(level.name.first())
        append(' ')
        append(tag.name)
        if (thread.isNotEmpty()) {
            append(" [")
            append(thread)
            append(']')
        }
        append(": ")
        append(message)
        if (fields.isNotEmpty()) {
            fields.entries.joinTo(this, separator = " ", prefix = " {", postfix = "}") { (key, value) ->
                "$key=$value"
            }
        }
        error?.let {
            append(" !")
            append(it)
        }
    }

    /** Everything in one entry matched against a query, for the panel's free-text filter. */
    internal fun matches(query: String): Boolean =
        message.contains(query, ignoreCase = true) ||
            tag.name.contains(query, ignoreCase = true) ||
            level.name.contains(query, ignoreCase = true) ||
            error?.contains(query, ignoreCase = true) == true ||
            fields.any { (key, value) ->
                key.contains(query, ignoreCase = true) || value.contains(query, ignoreCase = true)
            }

    private companion object {
        val ISO: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}

/**
 * The app's own log.
 *
 * ### Why this exists when Android already has one
 *
 * `Log.d` writes to a buffer this app cannot read back. On a release build it is stripped, on a
 * user's phone it is unreachable, and the one moment anybody needs it — a reader saying "it did
 * something strange" — is the moment it is gone. Everything here is readable *inside the app*, from
 * the admin panel, it survives the process, and it can be handed to a developer as a file.
 *
 * ### It is now written to disk, and why that changed
 *
 * This class used to keep its ring in memory only, and the reasoning was that a diagnostic aid
 * which survives the process is a file with a retention policy and a deletion obligation. That
 * reasoning was right about the obligation and wrong about the trade. The failures worth
 * diagnosing are the ones that kill the process — an uncaught exception, an ANR, the system
 * reclaiming the app in the background — and for every one of those, an in-memory log is empty by
 * the time anybody looks at it. A log that is guaranteed to be blank in the case it exists for is
 * not a log.
 *
 * So a [LogSink] may be attached. It is optional, it appends off the caller's thread, it is
 * bounded by bytes as well as by lines, and the panel can wipe it — which is the retention policy,
 * made an operator's button rather than a promise. Without one this class behaves exactly as it
 * did: a ring in memory and nothing on disk.
 *
 * ### What must never go in it
 *
 * **No secrets, no bodies, no tokens, no passwords, no API keys, no email addresses.** This is not
 * a convention call sites are asked to remember — [Redaction] enforces it on every message, every
 * field and every error string before the entry exists, so a call site that logs a bearer token
 * writes `[redacted]` and the token is not in the ring, not in the file, and not in the export.
 * [redact] exists for the other case, where an identifier genuinely has to be correlated across
 * lines: it keeps the shape and drops the content.
 *
 * Every method is safe to call from any thread.
 */
class AppLog(
    private val capacity: Int = CAPACITY,
    /**
     * Where entries are persisted, or null for memory only.
     *
     * Injected rather than constructed here because this module must stay unit-testable without a
     * device: the file sink takes a directory, and a test hands it a temporary one.
     */
    private val sink: LogSink? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val uptime: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private val sequence = AtomicLong(0)
    private val entriesMutable = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Oldest first, which is the order the file is written in and the order the export reads. */
    val entries: StateFlow<List<LogEntry>> = entriesMutable.asStateFlow()

    /**
     * The floor. Entries below it are dropped at the call site, so a disabled level costs a
     * comparison rather than a string.
     *
     * TRACE is off by default: it is the level that fires per tick and per frame, and leaving it on
     * would mean the ring holds two seconds of history when somebody needs two minutes. The panel
     * can lower it to reproduce something and raise it again afterwards, which is the only honest
     * way to get frame-level detail out of a build already in somebody's hands.
     */
    @Volatile
    var minimumLevel: LogLevel = LogLevel.DEBUG

    init {
        // What the previous process left behind, read once at construction. This is the half of
        // persistence that matters: writing entries nobody reads back is just wear on the flash.
        sink?.let { restored ->
            val previous = runCatching { restored.read(capacity) }.getOrDefault(emptyList())
            if (previous.isNotEmpty()) {
                entriesMutable.value = previous
                sequence.set(previous.maxOf(LogEntry::sequence))
            }
        }
    }

    fun log(
        level: LogLevel,
        tag: LogTag,
        message: String,
        fields: Map<String, String> = emptyMap(),
        error: Throwable? = null,
        epochMillis: Long = clock(),
        uptimeMillis: Long = uptime(),
    ) {
        if (level < minimumLevel) return
        val entry = LogEntry(
            sequence = sequence.incrementAndGet(),
            epochMillis = epochMillis,
            uptimeMillis = uptimeMillis,
            level = level,
            tag = tag,
            message = Redaction.scrub(message),
            fields = Redaction.scrub(fields),
            // The class name and the message, never the throwable. A retained exception holds its
            // stack, which holds every frame's locals — including, on a sign-in path, a password.
            error = error?.let { Redaction.scrub("${it::class.java.simpleName}: ${it.message.orEmpty()}") },
            thread = Thread.currentThread().name,
        )
        entriesMutable.update { current ->
            val next = current + entry
            if (next.size <= capacity) next else next.takeLast(capacity)
        }
        // After the ring, not before: a sink that throws — a full disk, a directory the system
        // removed under the app — must not be the reason an entry is missing from the screen.
        runCatching { sink?.append(entry) }
    }

    fun trace(tag: LogTag, message: String, fields: Map<String, String> = emptyMap()) =
        log(LogLevel.TRACE, tag, message, fields)

    fun debug(tag: LogTag, message: String, fields: Map<String, String> = emptyMap()) =
        log(LogLevel.DEBUG, tag, message, fields)

    fun info(tag: LogTag, message: String, fields: Map<String, String> = emptyMap()) =
        log(LogLevel.INFO, tag, message, fields)

    fun warn(tag: LogTag, message: String, fields: Map<String, String> = emptyMap()) =
        log(LogLevel.WARN, tag, message, fields)

    fun error(
        tag: LogTag,
        message: String,
        error: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    ) = log(LogLevel.ERROR, tag, message, fields, error)

    /**
     * Times a block and logs how long it took.
     *
     * Returns whatever the block returns, so it can be wrapped around an existing expression
     * without restructuring the code around it — which is what decides whether timing actually gets
     * added or is left for later.
     */
    inline fun <T> timed(tag: LogTag, message: String, block: () -> T): T {
        val started = System.nanoTime()
        return try {
            block()
        } finally {
            val millis = (System.nanoTime() - started) / 1_000_000
            log(
                level = if (millis > SLOW_MILLIS) LogLevel.WARN else LogLevel.DEBUG,
                tag = tag,
                message = message,
                fields = mapOf("ms" to millis.toString()),
            )
        }
    }

    /** The whole ring, oldest first, one line each — for the clipboard and the crash report. */
    fun dump(limit: Int = capacity): String =
        entriesMutable.value.takeLast(limit).joinToString("\n", transform = LogEntry::render)

    /**
     * Wipes the ring and the file.
     *
     * Both, always. Clearing only the ring would leave an operator believing they had deleted a log
     * that is still on disk and still travels with the next export — which is the worst possible
     * outcome for a control whose reason to exist is the deletion obligation.
     */
    fun clear() {
        entriesMutable.value = emptyList()
        runCatching { sink?.clear() }
    }

    /** What the panel's counters read, computed once rather than four times over the same list. */
    fun counters(now: Long = clock()): LogCounters = entriesMutable.value.counters(now)

    /**
     * How much room the persisted log is taking, or zero where nothing is persisted.
     *
     * On the panel because a diagnostic that writes to a phone's storage should say how much, next
     * to the button that empties it. An invisible growing file is the thing the in-memory-only
     * design was avoiding, and the honest way to keep the file is to show its cost.
     */
    fun persistedBytes(): Long = runCatching { sink?.sizeBytes() ?: 0L }.getOrDefault(0L)

    companion object {
        /**
         * How many lines are kept.
         *
         * Enough to hold the minutes before a failure at this app's rate — a few entries per
         * screen, a handful per request — without the list itself becoming the memory problem it is
         * meant to diagnose.
         */
        const val CAPACITY: Int = 600

        /** Past this, a timed block is logged as a warning rather than as a measurement. */
        const val SLOW_MILLIS: Long = 250

        /**
         * Keeps an identifier's shape and drops its content.
         *
         * For the cases where a value genuinely has to appear — correlating a signal id across
         * three lines, telling one account from another in a session bug — without the value being
         * worth reading. First and last character, length in between: `b••••8 (24)`. Short enough
         * to be useless, distinct enough to correlate.
         */
        fun redact(value: String?): String = when {
            value.isNullOrEmpty() -> "—"
            value.length <= 2 -> "•• (${value.length})"
            else -> "${value.first()}••••${value.last()} (${value.length})"
        }
    }
}

/**
 * The four numbers at the top of the panel.
 *
 * Counted rather than accumulated, because a counter that is incremented as entries arrive drifts
 * out of step with the ring the moment the ring drops its oldest line — and a panel saying "12
 * errors" above a list holding three is a panel nobody trusts twice.
 */
data class LogCounters(
    val total: Int = 0,
    val errors: Int = 0,
    val warnings: Int = 0,
    /** Errors inside the last hour: the difference between a problem now and a problem once. */
    val errorsLastHour: Int = 0,
    /** When the oldest line still held was written, or null on an empty log. */
    val oldestEpochMillis: Long? = null,
)

fun List<LogEntry>.counters(now: Long): LogCounters = LogCounters(
    total = size,
    errors = count { it.level == LogLevel.ERROR },
    warnings = count { it.level == LogLevel.WARN },
    errorsLastHour = count { it.level == LogLevel.ERROR && now - it.epochMillis <= HOUR_MILLIS },
    oldestEpochMillis = minByOrNull(LogEntry::epochMillis)?.epochMillis,
)

private const val HOUR_MILLIS = 60L * 60L * 1_000L
