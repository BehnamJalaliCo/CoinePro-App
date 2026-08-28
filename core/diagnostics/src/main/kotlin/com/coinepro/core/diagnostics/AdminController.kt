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

data class AdminUiState(
    val build: AdminBuildInfo,
    val panels: Map<MarketPlatform, PlatformPanel> = emptyMap(),
    val selected: MarketPlatform,
    val requests: List<RecordedRequest> = emptyList(),
    val failuresOnly: Boolean = false,
    /**
     * The narrative log, newest last.
     *
     * Beside the request table rather than instead of it. The table answers "which call failed";
     * this answers "and what was happening around it" — the socket that dropped, the screen the
     * reader was on, the token that refreshed. A failure nothing recorded is one somebody has to
     * reproduce before they can start.
     */
    val log: List<LogEntry> = emptyList(),
)

/**
 * Drives the admin panel.
 *
 * Per-platform state is kept in a map keyed by platform and never merged. That is the same rule the
 * rest of the app follows for quotes and balances, and it matters more here than anywhere: a panel
 * that showed one combined "connection status" would be the single screen in the product where the
 * two backends look like one system.
 */
class AdminController(
    private val build: AdminBuildInfo,
    private val platforms: List<PlatformBuildInfo>,
    private val probers: Map<MarketPlatform, EndpointProber>,
    private val requestLog: RequestLog,
    private val appLog: AppLog,
    private val scope: CoroutineScope,
    initialPlatform: MarketPlatform,
) {
    private val stateMutable = MutableStateFlow(
        AdminUiState(
            build = build,
            selected = initialPlatform,
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
    }

    fun select(platform: MarketPlatform) = stateMutable.update { it.copy(selected = platform) }

    fun toggleFailuresOnly() = stateMutable.update { it.copy(failuresOnly = !it.failuresOnly) }

    fun clearRequests() {
        requestLog.clear()
        appLog.clear()
    }

    /** The whole log as text, for the clipboard — which is how it reaches whoever can fix it. */
    fun logText(): String = appLog.dump()

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
            }
        }
    }

    fun setInstallId(platform: MarketPlatform, installId: String?) {
        stateMutable.updatePanel(platform) { it.copy(installId = maskSecret(installId)) }
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
