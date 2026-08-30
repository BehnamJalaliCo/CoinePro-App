package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What one platform's build was configured with.
 *
 * Supplied by the app rather than read here, because these come from `BuildConfig` and a core
 * module has no business reaching into the application's generated code.
 */
data class PlatformBuildInfo(
    val platform: MarketPlatform,
    val baseUrl: String?,
    val configured: Boolean = !baseUrl.isNullOrBlank(),
)

data class AdminBuildInfo(
    val versionName: String,
    val versionCode: String,
    val environment: String,
    val applicationId: String,
    val debuggable: Boolean,
    val firebaseConfigured: Boolean,
)

/** One platform's live picture, as the panel draws it. */
data class PlatformPanel(
    val platform: MarketPlatform,
    val build: PlatformBuildInfo,
    val probes: List<EndpointProbe> = emptyList(),
    val probing: Boolean = false,
    val installId: String = ABSENT,
)

/** Which of the panel's four sections is on screen. */
enum class AdminSection {
    /** Is anything wrong, and what. The section that answers the question the panel is opened with. */
    OVERVIEW,

    /** The log, its filters and its export. The one the owner asked for. */
    LOG,

    /** What the app said to which server and what came back. */
    NETWORK,

    /** The install itself: build, device, storage, notifications, and the panel's own controls. */
    SYSTEM,
}

/** What the last export attempt did, so the button can say something rather than nothing. */
enum class ExportOutcome { NONE, SHARED, SAVED, FAILED }

data class AdminUiState(
    val build: AdminBuildInfo,
    val selected: MarketPlatform,
    val panels: Map<MarketPlatform, PlatformPanel> = emptyMap(),
    val requests: List<RecordedRequest> = emptyList(),
    val failuresOnly: Boolean = false,
    /**
     * The whole log, oldest first and unfiltered.
     *
     * Kept whole in the state and narrowed at the point of reading, rather than stored already
     * filtered. Changing a filter must never destroy history: an operator who narrows to ERROR and
     * then wants the DEBUG line before it would otherwise have to reproduce the fault again.
     */
    val log: List<LogEntry> = emptyList(),
    val filter: LogFilter = LogFilter(),
    /** The sequence number of the entry whose fields are expanded, or null. One at a time. */
    val expandedEntry: Long? = null,
    val section: AdminSection = AdminSection.OVERVIEW,
    val gate: AdminGateState = AdminGateState(),
    val device: DeviceReport = DeviceReport(),
    val crash: Crash? = null,
    val logBytes: Long = 0,
    /**
     * What the log is currently writing at.
     *
     * In the state rather than read from [AppLog] at the call site, so the control shows what is
     * actually in force and moves when it is changed. A segmented control reading a `@Volatile`
     * field directly would not recompose when that field changed.
     */
    val verbosity: LogLevel = LogLevel.DEBUG,
    val exportOutcome: ExportOutcome = ExportOutcome.NONE,
)

/**
 * Drives the admin panel.
 *
 * ### What changed, and why the panel was worth rebuilding rather than patching
 *
 * The previous panel was a report with a probe button on it. It opened onto a red verdict it had
 * inferred from ordinary 401s, listed two hundred raw requests in the order they happened, and
 * offered a log it could copy to the clipboard and nowhere else. The three things an operator
 * actually needs from a panel like this — *is something wrong*, *what exactly*, and *give me the
 * file so somebody can fix it* — were the three it did worst.
 *
 * So the shape here is different in three ways that matter:
 *
 *  * The verdict is computed from faults that are genuinely faults ([findings]), not from every
 *    non-2xx status the process ever saw.
 *  * The log is a first-class object with filters and an export, rather than a copy button.
 *  * Nothing opens at all until [AdminGate] has been satisfied, because everything on this panel is
 *    a lever rather than a reading.
 *
 * ### Per-platform state is still never merged
 *
 * That rule is unchanged and it matters more here than anywhere: a panel that showed one combined
 * "connection status" would be the single screen in the product where the two backends look like
 * one system.
 */
class AdminController(
    private val build: AdminBuildInfo,
    private val platforms: List<PlatformBuildInfo>,
    private val probers: Map<MarketPlatform, EndpointProber>,
    private val requestLog: RequestLog,
    private val appLog: AppLog,
    private val scope: CoroutineScope,
    initialPlatform: MarketPlatform,
    /**
     * The door.
     *
     * Defaulted to a gate with no credential, which refuses every attempt. That is the deliberate
     * failure mode for a build that has not been wired up: the panel is unreachable rather than
     * open, and the lock screen says which of the two situations it is in. See [AdminGate].
     */
    private val gate: AdminGate = AdminGate(credential = null),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val stateMutable = MutableStateFlow(
        AdminUiState(
            build = build,
            selected = initialPlatform,
            verbosity = appLog.minimumLevel,
            panels = platforms.associate { info ->
                info.platform to PlatformPanel(
                    platform = info.platform,
                    build = info,
                    // Listed before anything is fired, so the surface is visible even offline.
                    probes = EndpointCatalog.forPlatform(info.platform)
                        .map { EndpointProbe(it, ProbeOutcome.SKIPPED) },
                )
            },
        ),
    )

    val state: StateFlow<AdminUiState> = stateMutable.asStateFlow()

    init {
        scope.launch {
            requestLog.entries.collect { entries ->
                stateMutable.update { it.copy(requests = entries) }
            }
        }
        scope.launch {
            appLog.entries.collect { entries ->
                stateMutable.update { it.copy(log = entries) }
            }
        }
        scope.launch {
            gate.state.collect { gateState ->
                stateMutable.update { it.copy(gate = gateState) }
            }
        }
    }

    /* ------------------------------------------------------------------- the door */

    fun unlock(username: String, password: String): Boolean = gate.submit(username, password)

    fun editingCredentials() = gate.editing()

    fun lock() = gate.lock()

    /* ------------------------------------------------------------------ navigation */

    fun select(platform: MarketPlatform) {
        appLog.info(LogTag.PLATFORM, "admin platform selected", mapOf("platform" to platform.name))
        stateMutable.update { it.copy(selected = platform) }
    }

    fun show(section: AdminSection) = stateMutable.update { it.copy(section = section) }

    /* ------------------------------------------------------------------------ log */

    fun setMinimumLevel(level: LogLevel) =
        stateMutable.update { it.copy(filter = it.filter.copy(minimumLevel = level)) }

    fun toggleTag(tag: LogTag) =
        stateMutable.update { it.copy(filter = it.filter.toggling(tag)) }

    fun setQuery(query: String) =
        stateMutable.update { it.copy(filter = it.filter.copy(query = query)) }

    fun setWindow(window: LogWindow) =
        stateMutable.update { it.copy(filter = it.filter.copy(window = window)) }

    fun clearFilter() = stateMutable.update { it.copy(filter = LogFilter()) }

    /** Tapping the expanded entry collapses it, which is what every list in this app does. */
    fun expand(sequence: Long) = stateMutable.update {
        it.copy(expandedEntry = if (it.expandedEntry == sequence) null else sequence)
    }

    /**
     * How much the app writes.
     *
     * Exposed as a control rather than fixed, because the two useful settings are opposites: TRACE
     * while reproducing something, WARN when the log is being left running to catch a rare fault
     * without the ring filling with per-frame noise before it happens.
     */
    fun setVerbosity(level: LogLevel) {
        appLog.minimumLevel = level
        appLog.info(LogTag.SECURITY, "log verbosity changed", mapOf("level" to level.name))
        stateMutable.update { it.copy(verbosity = level) }
    }

    /**
     * Wipes the log, in memory and on disk.
     *
     * The panel's answer to a retention obligation: whatever the app recorded, the person holding
     * it can destroy, in one action, with nothing left in a file they cannot see.
     */
    fun clearLog() {
        appLog.clear()
        stateMutable.update { it.copy(expandedEntry = null, logBytes = appLog.persistedBytes()) }
    }

    /* -------------------------------------------------------------------- network */

    fun toggleFailuresOnly() = stateMutable.update { it.copy(failuresOnly = !it.failuresOnly) }

    fun clearRequests() = requestLog.clear()

    /**
     * Fires every safe route for one platform, updating each row as its answer arrives.
     *
     * Sequential rather than parallel, deliberately: a burst of twenty simultaneous requests to one
     * host is indistinguishable from abuse, and the rate limiter would answer 429 to routes that
     * are perfectly healthy — turning the diagnostic into a source of false failures.
     */
    fun probe(platform: MarketPlatform) {
        val prober = probers[platform] ?: return
        if (stateMutable.value.panels[platform]?.probing == true) return

        stateMutable.updatePanel(platform) { it.copy(probing = true) }
        appLog.info(LogTag.NETWORK, "route audit started", mapOf("platform" to platform.name))
        scope.launch {
            try {
                for (endpoint in prober.endpoints()) {
                    val result = prober.probe(endpoint)
                    stateMutable.updatePanel(platform) { panel ->
                        panel.copy(
                            probes = panel.probes.map { row ->
                                if (row.endpoint == endpoint) result else row
                            },
                        )
                    }
                }
            } finally {
                stateMutable.updatePanel(platform) { it.copy(probing = false) }
                val missing = stateMutable.value.panels[platform]?.probes.orEmpty()
                    .count { it.outcome == ProbeOutcome.NOT_FOUND }
                appLog.log(
                    // A missing route is a finding, so the audit that found one says so at a level
                    // an operator filtering to warnings will still see.
                    level = if (missing > 0) LogLevel.WARN else LogLevel.INFO,
                    tag = LogTag.NETWORK,
                    message = "route audit finished",
                    fields = mapOf("platform" to platform.name, "missing" to missing.toString()),
                )
            }
        }
    }

    /* --------------------------------------------------------- what the app supplies */

    fun setInstallId(platform: MarketPlatform, installId: String?) {
        stateMutable.updatePanel(platform) { it.copy(installId = maskSecret(installId)) }
    }

    /**
     * The device reading and the last crash, handed in by the screen.
     *
     * Both need a `Context` and both are read once per visit rather than watched — a crash file
     * cannot change while the app that would write it is the one on screen, and a device reading
     * taken at the moment the panel opened is the reading the operator is looking at.
     */
    fun observe(device: DeviceReport, crash: Crash?) = stateMutable.update {
        it.copy(device = device, crash = crash, logBytes = appLog.persistedBytes())
    }

    fun clearCrash() = stateMutable.update { it.copy(crash = null) }

    fun exported(outcome: ExportOutcome) {
        appLog.info(LogTag.SECURITY, "diagnostics exported", mapOf("outcome" to outcome.name))
        stateMutable.update { it.copy(exportOutcome = outcome) }
    }

    /* ------------------------------------------------------------------- the file */

    /**
     * Everything the export needs, assembled from what this controller holds and what the screen
     * hands it.
     *
     * The log goes in **already filtered**, deliberately. An operator who narrowed to the two
     * minutes around a failure and then exported expects the file to be those two minutes; sending
     * all six hundred lines would make the filter a lie and the file harder to read than the panel.
     */
    fun exportContext(hub: ControlHub = ControlHub(), feedLabel: String? = null): DiagnosticContext {
        val current = stateMutable.value
        val now = clock()
        return DiagnosticContext(
            build = current.build,
            device = current.device,
            platforms = platforms,
            selected = current.selected,
            sessions = hub.sessions,
            feedLabel = feedLabel ?: hub.feed?.label,
            push = hub.push,
            venue = hub.venue,
            capabilities = hub.capabilities,
            probes = current.panels[current.selected]?.probes.orEmpty(),
            failures = current.requests.failureGroups(),
            counters = current.log.counters(now),
            findings = current.findings(now),
            filter = current.filter,
            entries = current.log.matching(current.filter, now),
            crash = current.crash,
            installIds = current.panels.mapValues { (_, panel) -> panel.installId },
        )
    }

    /** The whole filtered log as text, for the clipboard — the quick path when a file is too much. */
    fun logText(): String {
        val current = stateMutable.value
        return current.log
            .matching(current.filter, clock())
            .joinToString("\n", transform = LogEntry::render)
    }

    private fun MutableStateFlow<AdminUiState>.updatePanel(
        platform: MarketPlatform,
        transform: (PlatformPanel) -> PlatformPanel,
    ) = update { current ->
        val panel = current.panels[platform] ?: return@update current
        current.copy(panels = current.panels + (platform to transform(panel)))
    }
}

/** The rows the request list shows, after the failures-only filter. */
fun AdminUiState.visibleRequests(): List<RecordedRequest> =
    if (failuresOnly) requests.filter(RecordedRequest::failed) else requests

/** A one-line count for the platform tab, so a problem is visible without opening the section. */
fun AdminUiState.failureCount(platform: MarketPlatform): Int =
    requests.count { it.platform == platform && it.failed }

/** The log as the panel draws it: narrowed by the filter, newest first, capped for composition. */
fun AdminUiState.visibleLog(now: Long, limit: Int): List<LogEntry> =
    log.matching(filter, now).asReversed().take(limit)
