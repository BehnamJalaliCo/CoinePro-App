package com.coinepro.core.diagnostics

/**
 * How much history a reading covers.
 *
 * A window rather than a pair of timestamps because of how an operator actually uses this: they
 * have just reproduced the fault, and everything older than the last few minutes is noise standing
 * between them and it. Choosing two absolute times on a phone keyboard is a form; four buttons is
 * an answer.
 */
enum class LogWindow(val millis: Long?) {
    FIVE_MINUTES(5L * 60_000L),
    ONE_HOUR(60L * 60_000L),
    ONE_DAY(24L * 60L * 60_000L),

    /** Everything the ring and the file still hold. Null means no lower bound rather than zero. */
    ALL(null),
}

/**
 * What the panel is currently showing of the log, and what an export carries.
 *
 * One object rather than four pieces of screen state, for a reason that is not tidiness: the export
 * has to be filtered *the same way* the screen is. An operator who narrows to the two minutes
 * around a failure and then exports expects the file to be those two minutes; an export that
 * quietly sent all six hundred lines would make the filter a lie and the file unreadable. One value
 * drives both.
 *
 * The default is deliberately permissive — every level, every tag, all of time — because a panel
 * that opens onto a filtered log is a panel that opens onto an empty list, and an empty list reads
 * as a broken screen rather than as an applied filter.
 */
data class LogFilter(
    val minimumLevel: LogLevel = LogLevel.TRACE,
    /** Empty means every tag. An explicit set of all tags would mean the same and read as a filter. */
    val tags: Set<LogTag> = emptySet(),
    val query: String = "",
    val window: LogWindow = LogWindow.ALL,
) {
    /** Whether anything is actually narrowing the list, which is what the "clear" control needs. */
    val active: Boolean
        get() = minimumLevel != LogLevel.TRACE ||
            tags.isNotEmpty() ||
            query.isNotBlank() ||
            window != LogWindow.ALL

    fun toggling(tag: LogTag): LogFilter =
        copy(tags = if (tag in tags) tags - tag else tags + tag)
}

/**
 * The filter applied, oldest first.
 *
 * [now] is passed rather than read, so the same list filtered twice in one frame cannot produce two
 * different answers, and so a test can pin the window without waiting for a clock.
 *
 * The free-text match is deliberately over the whole entry — message, tag, level, error and every
 * field key and value. An operator typing `404` means "wherever that appears", and a search that
 * only looked at the message would miss every entry where the status is where a status belongs.
 */
fun List<LogEntry>.matching(filter: LogFilter, now: Long): List<LogEntry> {
    val query = filter.query.trim()
    val floor = filter.window.millis?.let { now - it }
    return filter { entry ->
        entry.level >= filter.minimumLevel &&
            (filter.tags.isEmpty() || entry.tag in filter.tags) &&
            (floor == null || entry.epochMillis >= floor) &&
            (query.isEmpty() || entry.matches(query))
    }
}
