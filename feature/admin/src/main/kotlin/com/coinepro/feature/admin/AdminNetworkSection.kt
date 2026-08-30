package com.coinepro.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.diagnostics.ABSENT
import com.coinepro.core.diagnostics.AdminUiState
import com.coinepro.core.diagnostics.EndpointProbe
import com.coinepro.core.diagnostics.FailureGroup
import com.coinepro.core.diagnostics.HubActions
import com.coinepro.core.diagnostics.PlatformPanel
import com.coinepro.core.diagnostics.ProbeOutcome
import com.coinepro.core.diagnostics.RecordedRequest
import com.coinepro.core.diagnostics.failureGroups
import com.coinepro.core.diagnostics.maskHost
import com.coinepro.core.diagnostics.visibleRequests
import java.time.Instant

/**
 * What the app said to which server, and what came back.
 *
 * ### Grouped first, raw second
 *
 * The old panel led with two hundred raw request rows in the order they happened, and the owner was
 * right that it was close to useless: a broken route fails on every retry, so the list became forty
 * copies of one fact with the other two problems somewhere below the fold. The grouped digest is
 * now the first thing here — one row per distinct failure, with a count and a last-seen — and the
 * raw list is underneath for the case where the *order* is the thing being read.
 *
 * ### The route audit is still here, and demoted
 *
 * "Run the probe" was the other thing named as useless, and the criticism was about billing rather
 * than about the check: a button labelled "run the probe" tells an operator nothing about why they
 * would. It is a contract check between the app's route catalogue and what the server actually
 * serves, it is the thing that found two dead endpoints nobody had noticed for months, and it is
 * one card near the bottom rather than the panel's headline.
 */
internal fun LazyListScope.networkSection(state: AdminUiState, actions: HubActions) {
    val groups = state.requests.filter { it.platform == state.selected }.failureGroups()

    item {
        SectionHeader(
            title = stringResource(R.string.admin_failures_title),
            detail = stringResource(R.string.admin_failures_body),
        )
    }
    if (groups.isEmpty()) {
        item { EmptyNote(R.string.admin_failures_empty) }
    } else {
        item { Rows(groups.size) { index -> FailureRow(groups[index]) } }
    }

    state.panels[state.selected]?.let { panel ->
        item { RoutesCard(panel, actions) }
        item { ConnectionCard(panel) }
    }

    item {
        SectionHeader(
            title = stringResource(R.string.admin_requests_title),
            detail = stringResource(R.string.admin_requests_count, count(state.requests.size)),
            actionLabel = stringResource(
                if (state.failuresOnly) R.string.admin_requests_all else R.string.admin_requests_failures,
            ),
            onAction = actions.onToggleFailuresOnly,
            secondaryLabel = stringResource(R.string.admin_requests_clear),
            onSecondary = actions.onClearRequests,
        )
    }
    val requests = state.visibleRequests()
    if (requests.isEmpty()) {
        item { EmptyNote(R.string.admin_requests_empty) }
    } else {
        item { Rows(requests.size) { index -> RequestRow(requests[index]) } }
    }
}

/**
 * One distinct failure.
 *
 * An expected refusal is drawn muted and labelled rather than hidden. Hiding it would leave an
 * operator wondering why the panel says nothing while their screen is empty of data; saying "this
 * is normal, you are signed out" answers the question the row raises.
 */
@Composable
private fun FailureRow(group: FailureGroup) {
    val accent = if (group.expected) CoineProColors.TextMuted else CoineProColors.Sell
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusRail(accent)
        Column(Modifier.weight(1f)) {
            Text(
                text = figure(group.path),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (group.expected) {
                    stringResource(R.string.admin_failures_expected)
                } else {
                    stringResource(R.string.admin_failures_count, count(group.count)) + " · " +
                        PersianDateTime.clock(Instant.ofEpochMilli(group.lastAtEpochMillis)) +
                        " · " + figure("${group.slowestMillis} ms")
                },
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        StatusPill(figure(group.status?.toString() ?: group.failure ?: ABSENT), accent)
        MethodChip(group.method)
    }
}

@Composable
private fun RequestRow(request: RecordedRequest) {
    val accent = when {
        !request.failed -> CoineProColors.Buy
        request.status == 401 || request.status == 403 -> CoineProColors.TextMuted
        else -> CoineProColors.Sell
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusRail(accent)
        Column(Modifier.weight(1f)) {
            Text(
                text = figure(request.path),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = (request.platform?.id ?: ABSENT) + " · " + figure("${request.durationMillis} ms"),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        StatusPill(figure(request.status?.toString() ?: request.failure ?: ABSENT), accent)
        MethodChip(request.method)
    }
}

@Composable
private fun RoutesCard(panel: PlatformPanel, actions: HubActions) {
    val reached = panel.probes.count { it.outcome == ProbeOutcome.REACHED }
    val alive = panel.probes.count { it.outcome == ProbeOutcome.UNAUTHORIZED }
    val missing = panel.probes.count { it.outcome == ProbeOutcome.NOT_FOUND }

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Link, R.string.admin_probe_title)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.OneHalf),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Tally(
                Modifier.weight(1f),
                stringResource(R.string.admin_probe_reached),
                count(reached),
                CoineProColors.Buy,
            )
            Tally(
                Modifier.weight(1f),
                stringResource(R.string.admin_probe_alive),
                count(alive),
                CoineProColors.TextSecondary,
            )
            Tally(
                Modifier.weight(1f),
                stringResource(R.string.admin_probe_missing),
                count(missing),
                if (missing > 0) CoineProColors.Sell else CoineProColors.TextMuted,
            )
        }
        Muted(stringResource(R.string.admin_probe_body))
        CoineProSecondaryButton(
            text = stringResource(
                if (panel.probing) R.string.admin_probe_running else R.string.admin_probe_run,
            ),
            onClick = { if (!panel.probing) actions.onProbe(panel.platform) },
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
        // Failures first: a reader scanning for what broke should not scroll past twenty healthy
        // routes to reach their own bad news.
        panel.probes.sortedBy { it.outcome.severity() }.forEachIndexed { index, probe ->
            if (index > 0) Divider()
            ProbeRow(probe)
        }
    }
}

@Composable
private fun ProbeRow(probe: EndpointProbe) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusRail(probe.outcome.colour())
        Column(Modifier.weight(1f)) {
            Text(
                text = figure(probe.endpoint.path),
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
        probe.status?.let { StatusPill(figure(it), probe.outcome.colour()) }
        MethodChip(probe.endpoint.method)
    }
}

@Composable
private fun ConnectionCard(panel: PlatformPanel) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Secure, R.string.admin_platform_title)
        Field(R.string.admin_base_url, figure(maskHost(panel.build.baseUrl)))
        Field(
            R.string.admin_platform_configured,
            stringResource(
                if (panel.build.configured) R.string.admin_configured else R.string.admin_not_configured,
            ),
            if (panel.build.configured) CoineProColors.Buy else CoineProColors.Warning,
        )
        Field(R.string.admin_install_id, figure(panel.installId))
        Muted(stringResource(R.string.admin_masking_note))
    }
}
