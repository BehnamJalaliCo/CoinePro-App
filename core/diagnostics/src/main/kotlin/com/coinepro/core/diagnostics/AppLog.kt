package com.coinepro.core.diagnostics

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

    /** A controller's state moving — loading, loaded, failed. */
    STATE,

    /** The chart: series loads, viewport, tools, drawings. */
    CHART,

    /** Push and local alerts: received, filtered, shown, tapped. */
    NOTIFICATION,

    /** Reads and writes to DataStore and Room. */
    STORAGE,

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
 */
data class LogEntry(
    val sequence: Long,
    val elapsedRealtimeMillis: Long,
    val level: LogLevel,
    val tag: LogTag,
    val message: String,
    val fields: Map<String, String> = emptyMap(),
    /** Present only on ERROR, and only ever the class name plus message — never a live throwable. */
    val error: String? = null,
) {
    /** One line, for the clipboard and the crash report. Stable, greppable, no locale in it. */
    fun render(): String = buildString {
        append(elapsedRealtimeMillis)
        append(' ')
        append(level.name.first())
        append(' ')
        append(tag.name)
        append(": ")
        append(message)
        if (fields.isNotEmpty()) {
            fields.entries.joinTo(this, separator = " ", prefix = " {", postfix = "}") { (k, v) ->
                "$k=$v"
            }
        }
        error?.let {
            append(" !")
            append(it)
        }
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
 * the diagnostics screen, and it travels with the crash report.
 *
 * It is also the answer to the owner's requirement that no problem stay hidden. A failure that
 * nothing recorded is a failure somebody has to reproduce before they can start; a failure with the
 * last two hundred lines around it is usually a failure somebody can just read.
 *
 * ### What must never go in it
 *
 * The same rule [RecordedRequest] follows and for the same reason: **no secrets, no bodies, no
 * tokens, no passwords, no API keys, no email addresses**. This log is on screen five taps from any
 * reader and it is attached to crash reports. [redact] exists for the cases where an identifier has
 * to be logged at all — it keeps the shape and drops the content, which is enough to correlate two
 * lines and not enough to be worth stealing.
 *
 * ### Bounded, in memory, and never written to disk
 *
 * A ring of [CAPACITY] entries. Not persisted, for the same reason the request log is not: a
 * diagnostic aid that survives the process is a file with a retention policy, a backup story and a
 * deletion obligation. What survives a crash is the tail that the crash report carries.
 *
 * Every method is safe to call from any thread.
 */
class AppLog(private val capacity: Int = CAPACITY) {

    private val sequence = AtomicLong(0)
    private val entriesMutable = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Newest first, which is the order anybody reads a log in. */
    val entries: StateFlow<List<LogEntry>> = entriesMutable.asStateFlow()

    /**
     * The floor. Entries below it are dropped at the call site, so a disabled level costs a
     * comparison rather than a string.
     *
     * TRACE is off by default: it is the level that fires per tick and per frame, and leaving it on
     * would mean the ring holds two seconds of history when somebody needs two minutes.
     */
    @Volatile
    var minimumLevel: LogLevel = LogLevel.DEBUG

    fun log(
        level: LogLevel,
        tag: LogTag,
        message: String,
        fields: Map<String, String> = emptyMap(),
        error: Throwable? = null,
        elapsedRealtimeMillis: Long = System.nanoTime() / 1_000_000,
    ) {
        if (level < minimumLevel) return
        val entry = LogEntry(
            sequence = sequence.incrementAndGet(),
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            level = level,
            tag = tag,
            message = message,
            fields = fields,
            // The class name and the message, never the throwable. A retained exception holds its
            // stack, which holds every frame's locals — including, on a sign-in path, a password.
            error = error?.let { "${it::class.java.simpleName}: ${it.message.orEmpty()}" },
        )
        entriesMutable.update { current ->
            val next = current + entry
            if (next.size <= capacity) next else next.takeLast(capacity)
        }
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

    fun clear() {
        entriesMutable.value = emptyList()
    }

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
