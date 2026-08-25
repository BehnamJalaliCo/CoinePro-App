package com.coinepro.feature.admin

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.diagnostics.ABSENT
import com.coinepro.core.diagnostics.AdminUiState
import com.coinepro.core.diagnostics.EndpointProbe
import com.coinepro.core.diagnostics.PlatformPanel
import com.coinepro.core.diagnostics.ProbeOutcome
import com.coinepro.core.diagnostics.RecordedRequest
import com.coinepro.core.diagnostics.failureCount
import com.coinepro.core.diagnostics.maskHost
import com.coinepro.core.diagnostics.visibleRequests
import com.coinepro.core.model.MarketPlatform

/**
 * The diagnostic panel behind five taps on the version number.
 *
 * It is not a management console: this app is a client, and neither backend exposes an admin API,
 * so anything claiming to manage users or revenue here would be a screen full of invented state.
 * What it does instead is answer the questions that have actually cost this project time — which
 * backend answered, what it said, which routes are alive, what this build was configured with.
 *
 * Two rules hold it together. **The platforms never merge**: every section belongs to one backend,
 * chosen by the switch at the top, because a combined view would be the one screen in the product
 * where the two look like a single system. And **nothing credential-shaped is shown in full** — the
 * panel is reachable in a shipping build, on a phone that gets handed around, in a screenshot
 * pasted into a support chat.
 */
@Composable
fun AdminScreen(
    state: AdminUiState,
    onSelectPlatform: (MarketPlatform) -> Unit,
    onProbe: (MarketPlatform) -> Unit,
    onToggleFailuresOnly: () -> Unit,
    onClearRequests: () -> Unit,
) {
    val panel = state.panels[state.selected]

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        item { Header() }
        item { BuildCard(state) }

        item {
            CoineProSegmentedControl(
                options = state.panels.keys.toList().map { platform ->
                    platform to platform.tabLabel(state.failureCount(platform))
                },
                selected = state.selected,
                onSelect = onSelectPlatform,
            )
        }

        if (panel != null) {
            item { PlatformCard(panel) }
            item { ProbeCard(panel, onProbe = { onProbe(panel.platform) }) }
        }

        item {
            RequestLogHeader(
                total = state.requests.size,
                failuresOnly = state.failuresOnly,
                onToggleFailuresOnly = onToggleFailuresOnly,
                onClear = onClearRequests,
            )
        }

        val requests = state.visibleRequests()
        if (requests.isEmpty()) {
            item { Muted(stringResource(R.string.admin_requests_empty)) }
        } else {
            items(requests.size, key = { requests[it].sequence }) { index ->
                RequestRow(requests[index])
            }
        }
    }
}

/* ------------------------------------------------------------------ cards */

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.admin_title),
            style = MaterialTheme.typography.headlineSmall,
            color = CoineProColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.admin_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
    }
}

@Composable
private fun BuildCard(state: AdminUiState) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(R.string.admin_build_title)
        Field(R.string.admin_version, BidiText.isolateLtr("${state.build.versionName} (${state.build.versionCode})"))
        Field(R.string.admin_environment, BidiText.isolateLtr(state.build.environment))
        Field(R.string.admin_application_id, BidiText.isolateLtr(state.build.applicationId))
        Field(
            R.string.admin_debuggable,
            stringResource(if (state.build.debuggable) R.string.admin_yes else R.string.admin_no),
            // A debuggable build reaching a reader is a real problem, not a detail.
            accent = if (state.build.debuggable) CoineProColors.Sell else null,
        )
        Field(
            R.string.admin_firebase,
            stringResource(
                if (state.build.firebaseConfigured) R.string.admin_configured else R.string.admin_not_configured,
            ),
        )
    }
}

@Composable
private fun PlatformCard(panel: PlatformPanel) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(R.string.admin_platform_title)
        Field(R.string.admin_base_url, BidiText.isolateLtr(maskHost(panel.build.baseUrl)))
        Field(
            R.string.admin_platform_configured,
            stringResource(
                if (panel.build.configured) R.string.admin_configured else R.string.admin_not_configured,
            ),
            accent = if (panel.build.configured) null else CoineProColors.Warning,
        )
        Field(R.string.admin_install_id, BidiText.isolateLtr(panel.installId))
        Muted(stringResource(R.string.admin_masking_note))
    }
}

@Composable
private fun ProbeCard(panel: PlatformPanel, onProbe: () -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(R.string.admin_probe_title)
        Muted(stringResource(R.string.admin_probe_body))

        val reached = panel.probes.count { it.outcome == ProbeOutcome.REACHED }
        val alive = panel.probes.count { it.outcome == ProbeOutcome.UNAUTHORIZED }
        val missing = panel.probes.count { it.outcome == ProbeOutcome.NOT_FOUND }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.OneHalf),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            Tally(R.string.admin_probe_reached, reached, CoineProColors.Buy)
            Tally(R.string.admin_probe_alive, alive, CoineProColors.TextSecondary)
            // The one number the panel exists for.
            Tally(R.string.admin_probe_missing, missing, CoineProColors.Sell)
        }

        CoineProSecondaryButton(
            text = stringResource(
                if (panel.probing) R.string.admin_probe_running else R.string.admin_probe_run,
            ),
            onClick = { if (!panel.probing) onProbe() },
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.OneHalf),
        )

        panel.probes.forEach { ProbeRow(it) }
    }
}

@Composable
private fun ProbeRow(probe: EndpointProbe) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(probe.outcome.colour())
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = BidiText.isolateLtr("${probe.endpoint.method} ${probe.endpoint.path}"),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(probe.outcome.labelRes()) +
                    (probe.status?.let { " · " + BidiText.isolateLtr(it.toString()) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = probe.outcome.colour(),
            )
        }
        Text(
            text = probe.endpoint.area,
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun RequestLogHeader(
    total: Int,
    failuresOnly: Boolean,
    onToggleFailuresOnly: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.admin_requests_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.admin_requests_count, total),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        Row {
            TextButton(onClick = onToggleFailuresOnly) {
                Text(
                    text = stringResource(
                        if (failuresOnly) R.string.admin_requests_all else R.string.admin_requests_failures,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.Accent,
                )
            }
            TextButton(onClick = onClear) {
                Text(
                    text = stringResource(R.string.admin_requests_clear),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun RequestRow(request: RecordedRequest) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = CoineProSpacing.Two,
            vertical = CoineProSpacing.OneHalf,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(if (request.failed) CoineProColors.Sell else CoineProColors.Buy)
            Text(
                text = BidiText.isolateLtr("${request.method} ${request.path}"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = BidiText.isolateLtr(
                    request.status?.toString() ?: request.failure ?: ABSENT,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (request.failed) CoineProColors.Sell else CoineProColors.TextSecondary,
            )
        }
        Text(
            text = (request.platform?.id ?: ABSENT) +
                " · " + BidiText.isolateLtr("${request.durationMillis} ms"),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/* ------------------------------------------------------------------ parts */

@Composable
private fun CardTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = CoineProColors.TextPrimary,
        modifier = Modifier.padding(bottom = CoineProSpacing.One),
    )
}

@Composable
private fun Field(@StringRes label: Int, value: String, accent: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = accent ?: CoineProColors.TextPrimary,
        )
    }
}

@Composable
private fun Tally(@StringRes label: Int, value: Int, colour: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = BidiText.isolateLtr(value.toString()),
            style = MaterialTheme.typography.titleMedium,
            color = colour,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Dot(colour: Color) {
    Column(modifier = Modifier.size(8.dp).background(colour, CoineProPillShape)) {}
}

@Composable
private fun Muted(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
}

@Composable
private fun MarketPlatform.tabLabel(failures: Int): String {
    val name = stringResource(
        when (this) {
            MarketPlatform.COINEPRO_FX -> R.string.admin_platform_forex
            MarketPlatform.TRADEYAR -> R.string.admin_platform_crypto
        },
    )
    return if (failures > 0) stringResource(R.string.admin_tab_with_failures, name, failures) else name
}

@Composable
private fun ProbeOutcome.colour(): Color = when (this) {
    ProbeOutcome.REACHED -> CoineProColors.Buy
    // A refusal proves something is listening, which is the answer the prober is really after.
    ProbeOutcome.UNAUTHORIZED -> CoineProColors.TextSecondary
    ProbeOutcome.NOT_FOUND -> CoineProColors.Sell
    ProbeOutcome.SERVER_ERROR -> CoineProColors.Warning
    ProbeOutcome.UNREACHABLE -> CoineProColors.Warning
    ProbeOutcome.SKIPPED -> CoineProColors.TextMuted
}

@StringRes
private fun ProbeOutcome.labelRes(): Int = when (this) {
    ProbeOutcome.REACHED -> R.string.admin_outcome_reached
    ProbeOutcome.UNAUTHORIZED -> R.string.admin_outcome_unauthorized
    ProbeOutcome.NOT_FOUND -> R.string.admin_outcome_not_found
    ProbeOutcome.SERVER_ERROR -> R.string.admin_outcome_server_error
    ProbeOutcome.UNREACHABLE -> R.string.admin_outcome_unreachable
    ProbeOutcome.SKIPPED -> R.string.admin_outcome_skipped
}
