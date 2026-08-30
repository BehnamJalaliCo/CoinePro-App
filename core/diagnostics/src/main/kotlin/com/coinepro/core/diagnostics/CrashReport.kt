package com.coinepro.core.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * The last crash, written to disk before the process dies.
 *
 * This exists because of a specific failure this app could not diagnose: the reader said "it
 * crashes the moment I sign in and drops me back to the first screen", and there was nothing to
 * read. On a phone with no debugger attached and no crash service wired up, a crash is a process
 * that vanishes — the app restarts at its first screen and every trace of why is gone with it.
 *
 * So the handler writes the stack trace to the app's own files directory and then hands the
 * exception to whatever handler was already installed. It does not swallow anything, does not
 * decide the app can continue, and does not try to be a crash reporter: it leaves one file behind
 * so the next launch can show what happened.
 *
 * Nothing is sent anywhere. The file lives in the app's private storage, is shown on the admin
 * screen behind the five-tap gate, and is cleared when the reader asks. A crash trace can carry a
 * URL and a symbol; it is treated as the reader's, not as telemetry.
 */
class CrashReport(
    private val context: Context,
    /**
     * The log to attach, or null to write the trace alone.
     *
     * A stack trace says *where* the app died and almost never *why*. The two hundred lines before
     * it — the socket that dropped, the token that refreshed, the screen the reader was on — are
     * what turn a report into a diagnosis, and they are gone the instant the process is. So they
     * are written with it.
     */
    private val appLog: AppLog? = null,
) {

    /** The most recent crash, or null when the app has never crashed since the file was cleared. */
    fun last(): Crash? {
        val file = file()
        if (!file.exists()) return null
        return runCatching {
            val text = file.readText()
            val at = file.lastModified()
            Crash(atEpochMillis = at, trace = text)
        }.getOrNull()
    }

    fun clear() {
        runCatching { file().delete() }
    }

    /**
     * Installs the handler.
     *
     * Chained rather than replacing: the platform's own handler is what actually kills the process
     * and shows the system dialog, and an app that keeps that from happening is an app that hangs
     * instead of crashing — which is worse to be on the end of and harder to report.
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(thread: Thread, error: Throwable) {
        // Recorded in the log as well as in the file, so the timeline has the death in it rather
        // than simply stopping. Best effort by nature: a persisted log writes on a background
        // thread, and the process may not survive long enough for that thread to be scheduled —
        // which is exactly why the tail is copied into the crash file below rather than relied on.
        appLog?.error(
            tag = LogTag.LIFECYCLE,
            message = "uncaught exception, process is going down",
            error = error,
            fields = mapOf("thread" to thread.name),
        )

        val writer = StringWriter()
        PrintWriter(writer).use { out ->
            out.println("thread: ${thread.name}")
            out.println()
            error.printStackTrace(out)
            appLog?.let { log ->
                out.println()
                out.println("--- log, oldest first ---")
                out.println(log.dump(LOG_LINES))
            }
        }
        // Truncated: a deeply nested cause chain can run to hundreds of kilobytes, and the part
        // that names the fault is at the top.
        file().writeText(writer.toString().take(MAX_CHARS))
    }

    private fun file(): File = File(context.filesDir, FILE_NAME)

    private companion object {
        const val FILE_NAME = "last-crash.txt"
        const val MAX_CHARS = 48_000

        /**
         * How much of the log travels with a crash.
         *
         * Two hundred lines is a few minutes at this app's rate — comfortably back past whatever
         * the reader did that led here, and short enough that the trace itself is still at the top
         * of the file where somebody will look first.
         */
        const val LOG_LINES = 200
    }
}

/** One recorded crash: when it happened, and the trace as it was printed. */
data class Crash(val atEpochMillis: Long, val trace: String) {

    /** The first line that names something in this app, which is where a reader should look. */
    val culprit: String?
        get() = trace.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("at com.coinepro.") }

    /** The exception's own line — its type and message. */
    val summary: String
        get() = trace.lineSequence()
            .map(String::trim)
            .firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?: trace.lineSequence().firstOrNull().orEmpty()
}
