package com.coinepro.feature.admin

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
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
 * It is not a management console — the product's real admin panels live on each server, where the
 * data and the authority are. This one answers a question those cannot: what is *this build on this
 * handset* actually doing, and which of the two backends is answering it.
 *
 * The layout follows the same rules as the rest of the app rather than inventing a "debug screen"
 * dialect. A verdict at hero size, the way Home leads with a balance; one figure per card carrying
 * the weight; cards separated by gap rather than by rules; and the single gold accent reserved for
 * the one action worth taking. A panel that looks like a log dump gets read like one — which is to
 * say, not at all, until something has already gone wrong.
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
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            top = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        item { VerdictBlock(state, panel) }

        item {
            CoineProSegmentedControl(
                options = state.panels.keys.toList().map { it to it.tabLabel() },
                selected = state.selected,
                onSelect = onSelectPlatform,
            )
        }

        if (panel != null) {
            item { ReachCard(panel, onProbe = { onProbe(panel.platform) }) }
            item { ConnectionCard(panel) }
        }
        item { BuildCard(state) }

        item {
            SectionHeader(
                title = stringResource(R.string.admin_requests_title),
                detail = stringResource(R.string.admin_requests_count, state.requests.size),
                actionLabel = stringResource(
                    if (state.failuresOnly) R.string.admin_requests_all else R.string.admin_requests_failures,
                ),
                onAction = onToggleFailuresOnly,
                secondaryLabel = stringResource(R.string.admin_requests_clear),
                onSecondary = onClearRequests,
            )
        }

        val requests = state.visibleRequests()
        if (requests.isEmpty()) {
            item { EmptyNote(R.string.admin_requests_empty) }
        } else {
            item {
                CoineProCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = CoineProSpacing.Two,
                        vertical = CoineProSpacing.One,
                    ),
                ) {
                    requests.forEachIndexed { index, request ->
                        if (index > 0) Divider()
                        RequestRow(request)
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- verdict */

/**
 * The one thing worth seeing before anything else: is this install healthy.
 *
 * Given hero treatment for the same reason the balance is on Home — a reader opens this screen
 * with one question, and a screen that makes them assemble the answer from six small rows has
 * answered a different one.
 */
@Composable
private fun VerdictBlock(state: AdminUiState, panel: PlatformPanel?) {
    val failures = state.requests.count(RecordedRequest::failed)
    val missing = panel?.probes.orEmpty().count { it.outcome == ProbeOutcome.NOT_FOUND }
    val healthy = failures == 0 && missing == 0

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.admin_title),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        Text(
            text = stringResource(
                when {
                    missing > 0 -> R.string.admin_verdict_missing
                    failures > 0 -> R.string.admin_verdict_failures
                    else -> R.string.admin_verdict_healthy
                },
            ),
            style = CoineProTextStyles.Balance,
            color = when {
                missing > 0 -> CoineProColors.Sell
                failures > 0 -> CoineProColors.Warning
                else -> CoineProColors.Buy
            },
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (healthy) CoineProIcons.Success else CoineProIcons.Warning,
                ),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (healthy) CoineProColors.Buy else CoineProColors.Warning,
            )
            Text(
                text = stringResource(R.string.admin_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

/* ------------------------------------------------------------------ cards */

/**
 * The route check, given the most prominent card because it is the panel's reason to exist.
 *
 * The three tallies are sized like figures rather than captions, and the one that matters — routes
 * serving nothing — is the only one allowed to turn red.
 */
@Composable
private fun ReachCard(panel: PlatformPanel, onProbe: () -> Unit) {
    val reached = panel.probes.count { it.outcome == ProbeOutcome.REACHED }
    val alive = panel.probes.count { it.outcome == ProbeOutcome.UNAUTHORIZED }
    val missing = panel.probes.count { it.outcome == ProbeOutcome.NOT_FOUND }

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Link, R.string.admin_probe_title)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.OneHalf),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Tally(Modifier.weight(1f), R.string.admin_probe_reached, reached, CoineProColors.Buy)
            Tally(Modifier.weight(1f), R.string.admin_probe_alive, alive, CoineProColors.TextSecondary)
            Tally(
                Modifier.weight(1f),
                R.string.admin_probe_missing,
                missing,
                if (missing > 0) CoineProColors.Sell else CoineProColors.TextMuted,
            )
        }

        Text(
            text = stringResource(R.string.admin_probe_body),
            modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )

        CoineProSecondaryButton(
            text = stringResource(
                if (panel.probing) R.string.admin_probe_running else R.string.admin_probe_run,
            ),
            onClick = { if (!panel.probing) onProbe() },
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.OneHalf),
        )

        // Failures first. A reader scanning this card is looking for what broke, and burying three
        // red rows under twenty healthy ones makes them scroll to find their own bad news.
        val ordered = panel.probes.sortedBy { it.outcome.severity() }
        ordered.forEachIndexed { index, probe ->
            if (index > 0) Divider()
            ProbeRow(probe)
        }
    }
}

@Composable
private fun ConnectionCard(panel: PlatformPanel) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Secure, R.string.admin_platform_title)
        Field(R.string.admin_base_url, BidiText.isolateLtr(maskHost(panel.build.baseUrl)))
        Field(
            R.string.admin_platform_configured,
            stringResource(
                if (panel.build.configured) R.string.admin_configured else R.string.admin_not_configured,
            ),
            accent = if (panel.build.configured) CoineProColors.Buy else CoineProColors.Warning,
        )
        Field(R.string.admin_install_id, BidiText.isolateLtr(panel.installId))
        Text(
            text = stringResource(R.string.admin_masking_note),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun BuildCard(state: AdminUiState) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Info, R.string.admin_build_title)
        Field(R.string.admin_version, BidiText.isolateLtr("${state.build.versionName} (${state.build.versionCode})"))
        Field(R.string.admin_environment, BidiText.isolateLtr(state.build.environment))
        Field(R.string.admin_application_id, BidiText.isolateLtr(state.build.applicationId))
        Field(
            R.string.admin_debuggable,
            stringResource(if (state.build.debuggable) R.string.admin_yes else R.string.admin_no),
            // A debuggable build in a reader's hands is a finding, not a detail.
            accent = if (state.build.debuggable) CoineProColors.Sell else CoineProColors.TextPrimary,
        )
        Field(
            R.string.admin_firebase,
            stringResource(
                if (state.build.firebaseConfigured) R.string.admin_configured else R.string.admin_not_configured,
            ),
        )
    }
}

/* ------------------------------------------------------------------- rows */

@Composable
private fun ProbeRow(probe: EndpointProbe) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusRail(probe.outcome.colour())
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = BidiText.isolateLtr(probe.endpoint.path),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(probe.outcome.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = probe.outcome.colour(),
            )
        }
        probe.status?.let { StatusPill(BidiText.isolateLtr(it.toString()), probe.outcome.colour()) }
        MethodChip(probe.endpoint.method)
    }
}

@Composable
private fun RequestRow(request: RecordedRequest) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val accent = if (request.failed) CoineProColors.Sell else CoineProColors.Buy
        StatusRail(accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = BidiText.isolateLtr(request.path),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = (request.platform?.id ?: ABSENT) + " · " +
                    BidiText.isolateLtr("${request.durationMillis} ms"),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        StatusPill(
            BidiText.isolateLtr(request.status?.toString() ?: request.failure ?: ABSENT),
            accent,
        )
        MethodChip(request.method)
    }
}

/* ------------------------------------------------------------------ parts */

/** A short coloured bar rather than a dot: legible at a glance down a column of rows. */
@Composable
private fun StatusRail(colour: Color) {
    Box(
        Modifier
            .width(3.dp)
            .height(28.dp)
            .background(colour, RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun StatusPill(text: String, colour: Color) {
    Text(
        text = text,
        modifier = Modifier
            .background(colour.copy(alpha = 0.12f), CoineProPillShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        style = MaterialTheme.typography.bodySmall,
        color = colour,
        fontWeight = FontWeight.Bold,
    )
}

/** Neutral on purpose — the verb is context, and colouring it would compete with the status. */
@Composable
private fun MethodChip(method: String) {
    Text(
        text = BidiText.isolateLtr(method),
        modifier = Modifier
            .background(CoineProColors.SurfaceElevated, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
}

@Composable
private fun Tally(modifier: Modifier, @StringRes label: Int, value: Int, colour: Color) {
    Column(
        modifier = modifier
            .background(CoineProColors.SurfaceElevated, MaterialTheme.shapes.medium)
            .padding(vertical = CoineProSpacing.OneHalf),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = BidiText.isolateLtr(value.toString()),
            style = CoineProTextStyles.RowFigure,
            color = colour,
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun CardHead(@DrawableRes icon: Int, @StringRes title: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = CoineProColors.TextSecondary,
        )
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CoineProColors.TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        }
        Row {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.bodySmall, color = CoineProColors.Accent)
            }
            TextButton(onClick = onSecondary) {
                Text(secondaryLabel, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
            }
        }
    }
}

@Composable
private fun Field(@StringRes label: Int, value: String, accent: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CoineProColors.Border))
}

@Composable
private fun EmptyNote(@StringRes text: Int) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun MarketPlatform.tabLabel(): String = stringResource(
    when (this) {
        MarketPlatform.COINEPRO_FX -> R.string.admin_platform_forex
        MarketPlatform.TRADEYAR -> R.string.admin_platform_crypto
    },
)

/** Sort key: what is broken comes first, what was never fired comes last. */
private fun ProbeOutcome.severity(): Int = when (this) {
    ProbeOutcome.NOT_FOUND -> 0
    ProbeOutcome.SERVER_ERROR -> 1
    ProbeOutcome.UNREACHABLE -> 2
    ProbeOutcome.REACHED -> 3
    ProbeOutcome.UNAUTHORIZED -> 4
    ProbeOutcome.SKIPPED -> 5
}

@Composable
private fun ProbeOutcome.colour(): Color = when (this) {
    ProbeOutcome.REACHED -> CoineProColors.Buy
    // A refusal proves something is listening, which is the answer the prober is really after.
    ProbeOutcome.UNAUTHORIZED -> CoineProColors.TextSecondary
    ProbeOutcome.NOT_FOUND -> CoineProColors.Sell
    ProbeOutcome.SERVER_ERROR, ProbeOutcome.UNREACHABLE -> CoineProColors.Warning
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
