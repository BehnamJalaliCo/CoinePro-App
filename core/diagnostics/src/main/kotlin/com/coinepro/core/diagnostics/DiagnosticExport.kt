package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Everything the export needs, gathered by the screen that has it.
 *
 * A parameter object rather than a dozen arguments, and assembled by the caller rather than read
 * here, for the reason the whole module follows: `core:diagnostics` observes the app and must not
 * depend on it. The screen already holds every one of these.
 */
data class DiagnosticContext(
    val build: AdminBuildInfo,
    val device: DeviceReport,
    val platforms: List<PlatformBuildInfo>,
    val selected: MarketPlatform,
    val sessions: List<SessionRow> = emptyList(),
    /** Already-resolved copy — the feed's own state strings belong to the market-data layer. */
    val feedLabel: String? = null,
    val push: PushStatus? = null,
    val venue: VenueStatus? = null,
    val capabilities: Map<MarketPlatform, ServerCapabilities> = emptyMap(),
    val probes: List<EndpointProbe> = emptyList(),
    val failures: List<FailureGroup> = emptyList(),
    val counters: LogCounters = LogCounters(),
    val findings: List<HealthFinding> = emptyList(),
    val filter: LogFilter = LogFilter(),
    /** Already filtered, in the order the panel shows: what the operator narrowed to is what ships. */
    val entries: List<LogEntry> = emptyList(),
    val crash: Crash? = null,
    val installIds: Map<MarketPlatform, String> = emptyMap(),
)

/**
 * The file an operator hands to a developer.
 *
 * ### What the owner asked for, and what that actually requires
 *
 * "Export the output and hand it to any developer" sounds like dumping the log to a file. It is
 * not. A log on its own answers *what the app did* and none of the four questions anybody reading
 * it asks first: which build is this, what phone is it on, what was the app's state when it
 * happened, and what exactly failed. So the log is the last section of this file and not the whole
 * of it. Everything above it is the context that makes the log readable by somebody who was not
 * holding the phone.
 *
 * ### Plain text, and why not JSON
 *
 * The recipient is a person, in a terminal, possibly on a phone, quite possibly reading it pasted
 * into a chat window. Plain text with `== SECTION ==` rules is greppable, diffable, survives being
 * pasted anywhere, and needs no tool to read. A JSON export would be better for a machine and there
 * is no machine — the moment there is one, the log lines are already a stable tab-separated format
 * on disk and that is what it should read.
 *
 * Every timestamp is UTC and ISO-8601, matching what the servers write, so the two logs line up
 * without anybody converting anything. Every number is Latin, for the same reason.
 *
 * ### The redaction guarantee
 *
 * Nothing that reaches this function can carry a credential: [AppLog] scrubs every entry before it
 * exists, [maskSecret] masks install ids and push tokens at the boundary, and [maskHost] hides the
 * hostnames. The whole rendered file is then passed through [Redaction] one final time. That last
 * pass is not doubt about the first — it is there for the text that never went through the log at
 * all, such as a base URL misconfigured with a query string on the end of it, and for whatever a
 * future caller adds to [DiagnosticContext] without reading this note.
 */
object DiagnosticExport {

    const val MIME: String = "text/plain"

    private val ISO: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    /**
     * A file name that sorts and that says what it is.
     *
     * Latin digits and UTC, with the colons dropped: a colon is legal in the name on the phone and
     * illegal on the Windows machine somebody will eventually save it to, and a file that will not
     * save is an export that did not happen.
     */
    private val FILE_STAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    fun fileName(atEpochMillis: Long): String =
        "coinepro-diagnostics-" + FILE_STAMP.format(Instant.ofEpochMilli(atEpochMillis)) + ".txt"

    fun render(context: DiagnosticContext, atEpochMillis: Long): String {
        val body = buildString {
            header(context, atEpochMillis)
            verdict(context)
            buildSection(context)
            deviceSection(context.device)
            platformSection(context)
            stateSection(context)
            failureSection(context)
            probeSection(context)
            crashSection(context.crash)
            logSection(context)
            footer()
        }
        return Redaction.scrub(body)
    }

    /* ------------------------------------------------------------------ sections */

    private fun StringBuilder.header(context: DiagnosticContext, at: Long) {
        rule("COINEPRO DIAGNOSTIC REPORT")
        field("generated", ISO.format(Instant.ofEpochMilli(at)))
        field("app", "${context.build.versionName} (${context.build.versionCode})")
        field("environment", context.build.environment)
        field("platform in view", context.selected.name)
        appendLine()
        appendLine("All times are UTC. All figures are Latin. Credentials, tokens, hostnames and")
        appendLine("install identifiers are masked — this file is meant to be shared.")
        appendLine()
    }

    private fun StringBuilder.verdict(context: DiagnosticContext) {
        rule("VERDICT")
        if (context.findings.isEmpty()) {
            appendLine("No fault detected at the time of export.")
        } else {
            // Named in the same words the panel used, so the operator's screenshot and the file
            // agree. A report that disagrees with the screen it came from wastes the first reply.
            context.findings.forEach { finding ->
                appendLine("${finding.kind.name}  x${finding.count}")
            }
        }
        appendLine()
    }

    private fun StringBuilder.buildSection(context: DiagnosticContext) {
        rule("BUILD")
        field("applicationId", context.build.applicationId)
        field("versionName", context.build.versionName)
        field("versionCode", context.build.versionCode)
        field("environment", context.build.environment)
        field("debuggable", context.build.debuggable.toString())
        field("firebaseConfigured", context.build.firebaseConfigured.toString())
        appendLine()
    }

    private fun StringBuilder.deviceSection(device: DeviceReport) {
        rule("DEVICE")
        field("manufacturer", device.manufacturer)
        field("model", device.model)
        field("android", "${device.androidRelease} (API ${device.sdkInt})")
        field("abi", device.abi)
        field("locale", device.locale)
        field("layoutDirection", if (device.layoutDirectionRtl) "rtl" else "ltr")
        field("heap", "${device.usedHeapMegabytes} MB used of ${device.maxHeapMegabytes} MB")
        field("freeStorage", "${device.freeStorageMegabytes} MB")
        field("logOnDisk", "${device.logBytes} bytes")
        appendLine()
    }

    private fun StringBuilder.platformSection(context: DiagnosticContext) {
        rule("PLATFORMS")
        context.platforms.forEach { platform ->
            field(platform.platform.name + ".baseUrl", maskHost(platform.baseUrl))
            field(platform.platform.name + ".configured", platform.configured.toString())
            field(
                platform.platform.name + ".installId",
                context.installIds[platform.platform] ?: ABSENT,
            )
            context.capabilities[platform.platform]?.let { capabilities ->
                field(platform.platform.name + ".emailPassword", capabilities.emailPassword.render())
                field(platform.platform.name + ".google", capabilities.google.render())
                field(platform.platform.name + ".telegram", capabilities.telegram.render())
                field(platform.platform.name + ".push", capabilities.push.render())
                field(platform.platform.name + ".chartVision", capabilities.chartVision.render())
            }
        }
        appendLine()
    }

    private fun StringBuilder.stateSection(context: DiagnosticContext) {
        rule("STATE AT EXPORT")
        context.sessions.forEach { session ->
            field(
                "session." + session.platform.name,
                if (session.signedIn) "signed-in" else "signed-out",
            )
        }
        field("feed", context.feedLabel ?: ABSENT)
        context.push?.let { push ->
            field("push.permission", push.permission.name)
            field("push.serverEnabled", push.serverEnabled.render())
            // Masked at the source. The hint is enough to answer "is this the token the server
            // registered", which is the only question anybody asks of it.
            field("push.token", push.tokenHint)
            field("push.newSignals", push.newSignals.toString())
            field("push.signalUpdates", push.signalUpdates.toString())
            field("push.priceAlerts", push.priceAlerts.toString())
        }
        context.venue?.let { venue ->
            field("venue.name", venue.name)
            field("venue.configured", venue.configured.toString())
            field("venue.connected", venue.connected.toString())
        }
        field("log.total", context.counters.total.toString())
        field("log.errors", context.counters.errors.toString())
        field("log.warnings", context.counters.warnings.toString())
        field("log.errorsLastHour", context.counters.errorsLastHour.toString())
        appendLine()
    }

    private fun StringBuilder.failureSection(context: DiagnosticContext) {
        rule("FAILING REQUESTS")
        if (context.failures.isEmpty()) {
            appendLine("None recorded.")
        } else {
            appendLine("count  status  ms(worst)  last(UTC)             route")
            context.failures.forEach { group ->
                append(group.count.toString().padEnd(7))
                append((group.status?.toString() ?: group.failure ?: ABSENT).padEnd(8))
                append(group.slowestMillis.toString().padEnd(11))
                append(ISO.format(Instant.ofEpochMilli(group.lastAtEpochMillis)).padEnd(22))
                append(group.platform?.name ?: ABSENT)
                append(' ')
                append(group.method)
                append(' ')
                appendLine(group.path)
            }
        }
        appendLine()
    }

    private fun StringBuilder.probeSection(context: DiagnosticContext) {
        val fired = context.probes.filter { it.outcome != ProbeOutcome.SKIPPED }
        rule("ROUTE AUDIT")
        if (fired.isEmpty()) {
            appendLine("Not run in this session.")
        } else {
            fired.forEach { probe ->
                append(probe.outcome.name.padEnd(14))
                append((probe.status?.toString() ?: ABSENT).padEnd(6))
                append(probe.endpoint.method.padEnd(7))
                appendLine(probe.endpoint.path)
            }
        }
        appendLine()
    }

    private fun StringBuilder.crashSection(crash: Crash?) {
        rule("LAST CRASH")
        if (crash == null) {
            appendLine("None since the crash file was last cleared.")
        } else {
            field("at", ISO.format(Instant.ofEpochMilli(crash.atEpochMillis)))
            field("summary", crash.summary)
            field("firstAppFrame", crash.culprit ?: ABSENT)
            appendLine()
            appendLine(crash.trace)
        }
        appendLine()
    }

    private fun StringBuilder.logSection(context: DiagnosticContext) {
        rule("LOG")
        field("filter.minimumLevel", context.filter.minimumLevel.name)
        field(
            "filter.tags",
            context.filter.tags.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name } ?: "all",
        )
        field("filter.window", context.filter.window.name)
        field("filter.query", context.filter.query.ifBlank { "none" })
        field("lines", context.entries.size.toString())
        appendLine()
        if (context.entries.isEmpty()) {
            appendLine("No entries match the filter this was exported under.")
        } else {
            context.entries.forEach { appendLine(it.render()) }
        }
        appendLine()
    }

    private fun StringBuilder.footer() {
        rule("END")
    }

    /* --------------------------------------------------------------------- parts */

    private fun StringBuilder.rule(title: String) {
        appendLine("== $title " + "=".repeat((RULE_WIDTH - title.length - 4).coerceAtLeast(3)))
    }

    private fun StringBuilder.field(name: String, value: String) {
        append(name.padEnd(NAME_WIDTH))
        append(": ")
        appendLine(value)
    }

    /**
     * Null renders as unknown rather than as false.
     *
     * The distinction the whole capability model rests on: a server that has not been asked and a
     * server that answered no are different facts, and an export that flattened them would send a
     * developer looking for why a feature is switched off that nobody ever enquired about.
     */
    private fun Boolean?.render(): String = when (this) {
        true -> "on"
        false -> "off"
        null -> "unknown"
    }

    private const val RULE_WIDTH = 78
    private const val NAME_WIDTH = 26
}
