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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
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
import com.coinepro.core.diagnostics.Appearance
import com.coinepro.core.diagnostics.ControlHub
import com.coinepro.core.diagnostics.EndpointProbe
import com.coinepro.core.diagnostics.FeedStatus
import com.coinepro.core.diagnostics.HUB_ON
import com.coinepro.core.diagnostics.HubActions
import com.coinepro.core.diagnostics.HubSection
import com.coinepro.core.diagnostics.HubTile
import com.coinepro.core.diagnostics.HubTone
import com.coinepro.core.diagnostics.LogEntry
import com.coinepro.core.diagnostics.LogLevel
import com.coinepro.core.diagnostics.PlatformPanel
import com.coinepro.core.diagnostics.ProbeOutcome
import com.coinepro.core.diagnostics.PushPermission
import com.coinepro.core.diagnostics.PushPreferenceKey
import com.coinepro.core.diagnostics.PushStatus
import com.coinepro.core.diagnostics.RecordedRequest
import com.coinepro.core.diagnostics.ServerCapabilities
import com.coinepro.core.diagnostics.SessionRow
import com.coinepro.core.diagnostics.VenueStatus
import com.coinepro.core.diagnostics.hubTiles
import com.coinepro.core.diagnostics.maskHost
import com.coinepro.core.diagnostics.visibleRequests
import com.coinepro.core.model.MarketPlatform

/**
 * The control hub, five taps behind the version number.
 *
 * The product's real admin panels live on each server, where the data and the authority are. This
 * one owns the half neither of them can reach: the app on this handset. It is a hub rather than a
 * report because every section here has a lever — sign a platform out, restart the feed, clear a
 * cache, ask for the notification permission, flip a push preference, change the language, probe
 * the routes.
 *
 * Compact by design. Six tiles answer "is anything wrong" in one glance, and the sections beneath
 * them are where a reader goes once a tile has told them where to look. A tile and its section read
 * the same field, so they can never disagree.
 *
 * Everything is scoped to the platform in the switch. The two backends are separate accounts on
 * separate servers, and a hub that merged them would be the one screen in the product where they
 * look like a single system.
 */
@Composable
fun AdminScreen(
    state: AdminUiState,
    hub: ControlHub = ControlHub(),
    actions: HubActions = HubActions(),
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
        item { Verdict(state, hub, panel) }

        item {
            CoineProSegmentedControl(
                options = state.panels.keys.toList().map { it to it.label() },
                selected = state.selected,
                onSelect = actions.onSelectPlatform,
            )
        }

        item { TileGrid(state.hubTiles(hub)) }

        hub.sessions.takeIf { it.isNotEmpty() }?.let { sessions ->
            item { SessionCard(sessions, state.selected, actions) }
        }
        hub.feed?.let { item { FeedCard(it, actions) } }
        hub.push?.let { item { PushCard(it, actions) } }
        hub.venue?.let { item { VenueCard(it) } }
        hub.capabilities[state.selected]?.let {
            item { CapabilitiesCard(it, state.selected, actions) }
        }
        hub.appearance?.let { item { AppearanceCard(it, actions) } }

        if (panel != null) item { RoutesCard(panel, actions) }
        item { ConnectionCard(panel) }
        item { BuildCard(state) }

        item {
            SectionHeader(
                title = stringResource(R.string.admin_requests_title),
                detail = stringResource(R.string.admin_requests_count, state.requests.size),
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
            item {
                Rows(requests.size) { index -> RequestRow(requests[index]) }
            }
        }

        // The narrative, under the table. The table says which call failed; this says what was
        // happening around it — the screen the reader was on, the socket that dropped, the session
        // that changed. Copying it is the point: it is how a problem reaches whoever can fix it.
        item {
            SectionHeader(
                title = stringResource(R.string.admin_log_title),
                detail = stringResource(R.string.admin_log_count, state.log.size),
                actionLabel = stringResource(R.string.admin_log_copy),
                onAction = actions.onCopyLog,
            )
        }
        if (state.log.isEmpty()) {
            item { EmptyNote(R.string.admin_log_empty) }
        } else {
            item {
                // Newest first, which is the order anybody reads a log in — and capped, because
                // six hundred rows inside a `LazyColumn` item is six hundred rows composed at once.
                val recent = state.log.asReversed().take(LOG_ROWS)
                Rows(recent.size) { index -> LogRow(recent[index]) }
            }
        }
    }
}

/**
 * One log line, in the monospaced shape the rest of this screen uses for machine text.
 *
 * The level is a coloured letter rather than a word: at this density the eye is scanning for the
 * red ones, and «هشدار» spelled out on every row would push the message off the edge.
 */
@Composable
private fun LogRow(entry: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = entry.level.name.first().toString(),
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = when (entry.level) {
                LogLevel.ERROR -> CoineProColors.Sell
                LogLevel.WARN -> CoineProColors.Warning
                LogLevel.INFO -> CoineProColors.TextSecondary
                else -> CoineProColors.TextDisabled
            },
        )
        Text(
            text = BidiText.isolateLtr(entry.render().substringAfter(' ').substringAfter(' ')),
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = if (entry.level == LogLevel.ERROR) {
                CoineProColors.TextPrimary
            } else {
                CoineProColors.TextSecondary
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * How many log lines the screen composes.
 *
 * The ring holds six hundred and the clipboard gets all of them; this is what is *drawn*, and it is
 * capped because a `LazyColumn` item is composed whole — six hundred rows inside one item is six
 * hundred rows built on the frame the section scrolls into view.
 */
private const val LOG_ROWS = 120

/* --------------------------------------------------------------- overview */

@Composable
private fun Verdict(state: AdminUiState, hub: ControlHub, panel: PlatformPanel?) {
    val missing = panel?.probes.orEmpty().count { it.outcome == ProbeOutcome.NOT_FOUND }
    val failures = state.requests.count(RecordedRequest::failed)
    val healthy = missing == 0 && failures == 0

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
                painter = painterResource(if (healthy) CoineProIcons.Success else CoineProIcons.Warning),
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

/** Six subsystems, three to a row. The whole point is that it fits without scrolling. */
@Composable
private fun TileGrid(tiles: List<HubTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                row.forEach { Tile(Modifier.weight(1f), it) }
            }
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, tile: HubTile) {
    val colour = tile.tone.colour()
    Column(
        modifier = modifier
            .background(CoineProColors.Surface, MaterialTheme.shapes.medium)
            .padding(vertical = CoineProSpacing.OneHalf, horizontal = CoineProSpacing.One),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painter = painterResource(tile.section.icon()),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colour,
        )
        Text(
            text = tile.value.asDisplayValue(),
            style = CoineProTextStyles.RowFigure,
            color = colour,
        )
        Text(
            text = stringResource(tile.section.labelRes()),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/* ------------------------------------------------------------------ cards */

@Composable
private fun SessionCard(
    sessions: List<SessionRow>,
    selected: MarketPlatform,
    actions: HubActions,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Locked, R.string.admin_session_title)
        sessions.forEachIndexed { index, row ->
            if (index > 0) Divider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusRail(if (row.signedIn) CoineProColors.Buy else CoineProColors.TextMuted)
                Spacer(Modifier.width(CoineProSpacing.One))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.platform.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        // Server wording where there is any; the app never restates a session's
                        // condition in its own words.
                        text = row.detail ?: stringResource(
                            if (row.signedIn) R.string.admin_session_in else R.string.admin_session_out,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }
                if (row.signedIn) {
                    TextButton(onClick = { actions.onSignOut(row.platform) }) {
                        Text(
                            stringResource(R.string.admin_sign_out),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.Sell,
                        )
                    }
                }
            }
        }
        if (sessions.any(SessionRow::signedIn)) {
            CoineProSecondaryButton(
                text = stringResource(R.string.admin_sign_out_everywhere),
                onClick = actions.onSignOutEverywhere,
                modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
            )
        }
        Spacer(Modifier.height(0.dp))
        // Named rather than implied: signing one platform out leaves the other signed in, which is
        // the correct behaviour for two separate accounts and surprises people who expect otherwise.
        Muted(stringResource(R.string.admin_session_note, selected.label()))
    }
}

@Composable
private fun FeedCard(feed: FeedStatus, actions: HubActions) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.TrendUp, R.string.admin_feed_title)
        Field(R.string.admin_feed_state, feed.label, feed.tone.colour())
        Field(R.string.admin_feed_symbols, BidiText.isolateLtr(feed.subscribedSymbols.toString()))
        Field(R.string.admin_feed_cache, feed.cacheAgeLabel ?: ABSENT)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            CoineProSecondaryButton(
                text = stringResource(R.string.admin_feed_restart),
                onClick = actions.onRestartFeed,
                modifier = Modifier.weight(1f),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.admin_sync_now),
                onClick = actions.onSyncNow,
                modifier = Modifier.weight(1f),
            )
        }
        CoineProSecondaryButton(
            text = stringResource(R.string.admin_clear_market_cache),
            onClick = actions.onClearMarketCache,
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
    }
}

@Composable
private fun PushCard(push: PushStatus, actions: HubActions) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Activity, R.string.admin_push_title)

        // Both halves, separately. A granted permission on a server that cannot send is not "push
        // is on", and the fix for each is in a different place.
        Field(
            R.string.admin_push_permission,
            stringResource(push.permission.labelRes()),
            if (push.permission == PushPermission.GRANTED) CoineProColors.Buy else CoineProColors.Warning,
        )
        Field(
            R.string.admin_push_server,
            stringResource(
                when (push.serverEnabled) {
                    true -> R.string.admin_on
                    false -> R.string.admin_off
                    null -> R.string.admin_unknown
                },
            ),
            when (push.serverEnabled) {
                true -> CoineProColors.Buy
                false -> CoineProColors.Warning
                null -> CoineProColors.TextMuted
            },
        )
        Field(R.string.admin_push_token, BidiText.isolateLtr(push.tokenHint))

        when (push.permission) {
            PushPermission.AVAILABLE -> CoineProSecondaryButton(
                text = stringResource(R.string.admin_push_request),
                onClick = actions.onRequestPushPermission,
                modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
            )
            PushPermission.DENIED -> CoineProSecondaryButton(
                text = stringResource(R.string.admin_push_settings),
                onClick = actions.onOpenPushSettings,
                modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
            )
            else -> Unit
        }

        Divider()
        Toggle(R.string.admin_push_new_signals, push.newSignals) {
            actions.onSetPushPreference(PushPreferenceKey.NEW_SIGNALS, it)
        }
        Toggle(R.string.admin_push_signal_updates, push.signalUpdates) {
            actions.onSetPushPreference(PushPreferenceKey.SIGNAL_UPDATES, it)
        }
        Toggle(R.string.admin_push_price_alerts, push.priceAlerts) {
            actions.onSetPushPreference(PushPreferenceKey.PRICE_ALERTS, it)
        }

        CoineProSecondaryButton(
            text = stringResource(R.string.admin_push_reregister),
            onClick = actions.onReRegisterPushToken,
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
    }
}

@Composable
private fun VenueCard(venue: VenueStatus) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Wallet, R.string.admin_venue_title)
        Field(R.string.admin_venue_name, BidiText.isolateLtr(venue.name))
        Field(
            R.string.admin_venue_configured,
            stringResource(if (venue.configured) R.string.admin_yes else R.string.admin_no),
        )
        Field(
            R.string.admin_venue_connected,
            stringResource(if (venue.connected) R.string.admin_yes else R.string.admin_no),
            if (venue.connected) CoineProColors.Buy else CoineProColors.Warning,
        )
        Muted(stringResource(R.string.admin_venue_note))
    }
}

@Composable
private fun CapabilitiesCard(
    capabilities: ServerCapabilities,
    platform: MarketPlatform,
    actions: HubActions,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Info, R.string.admin_capabilities_title)
        Capability(R.string.admin_capability_email, capabilities.emailPassword)
        Capability(R.string.admin_capability_google, capabilities.google)
        Capability(R.string.admin_capability_telegram, capabilities.telegram)
        Capability(R.string.admin_capability_push, capabilities.push)
        Capability(R.string.admin_capability_vision, capabilities.chartVision)
        Field(
            R.string.admin_capability_symbols,
            capabilities.symbolCount?.let { BidiText.isolateLtr(it.toString()) } ?: ABSENT,
        )
        CoineProSecondaryButton(
            text = stringResource(R.string.admin_capabilities_refresh),
            onClick = { actions.onRefreshCapabilities(platform) },
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
        Muted(stringResource(R.string.admin_capabilities_note))
    }
}

@Composable
private fun AppearanceCard(appearance: Appearance, actions: HubActions) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Settings, R.string.admin_appearance_title)
        CoineProSegmentedControl(
            options = listOf(
                "fa" to stringResource(R.string.admin_language_fa),
                "en" to stringResource(R.string.admin_language_en),
            ),
            selected = appearance.languageTag,
            onSelect = actions.onSetLanguage,
        )
        Muted(stringResource(R.string.admin_language_note))
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
            Tally(Modifier.weight(1f), R.string.admin_probe_reached, reached, CoineProColors.Buy)
            Tally(Modifier.weight(1f), R.string.admin_probe_alive, alive, CoineProColors.TextSecondary)
            Tally(
                Modifier.weight(1f),
                R.string.admin_probe_missing,
                missing,
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
private fun ConnectionCard(panel: PlatformPanel?) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Secure, R.string.admin_platform_title)
        Field(R.string.admin_base_url, BidiText.isolateLtr(maskHost(panel?.build?.baseUrl)))
        Field(
            R.string.admin_platform_configured,
            stringResource(
                if (panel?.build?.configured == true) R.string.admin_configured else R.string.admin_not_configured,
            ),
            if (panel?.build?.configured == true) CoineProColors.Buy else CoineProColors.Warning,
        )
        Field(R.string.admin_install_id, BidiText.isolateLtr(panel?.installId ?: ABSENT))
        Muted(stringResource(R.string.admin_masking_note))
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
            if (state.build.debuggable) CoineProColors.Sell else CoineProColors.TextPrimary,
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
        Column(Modifier.weight(1f)) {
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
    val accent = if (request.failed) CoineProColors.Sell else CoineProColors.Buy
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusRail(accent)
        Column(Modifier.weight(1f)) {
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

@Composable
private fun Rows(count: Int, row: @Composable (Int) -> Unit) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One),
    ) {
        repeat(count) { index ->
            if (index > 0) Divider()
            row(index)
        }
    }
}

@Composable
private fun Toggle(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CoineProColors.OnAccent,
                checkedTrackColor = CoineProColors.AccentFill,
                uncheckedThumbColor = CoineProColors.TextMuted,
                uncheckedTrackColor = CoineProColors.SurfaceElevated,
            ),
        )
    }
}

@Composable
private fun Capability(@StringRes label: Int, value: Boolean?) {
    Field(
        label,
        stringResource(
            when (value) {
                true -> R.string.admin_on
                false -> R.string.admin_off
                null -> R.string.admin_unknown
            },
        ),
        when (value) {
            true -> CoineProColors.Buy
            false -> CoineProColors.TextMuted
            null -> CoineProColors.TextMuted
        },
    )
}

@Composable
private fun StatusRail(colour: Color) {
    Box(Modifier.width(3.dp).height(28.dp).background(colour, RoundedCornerShape(2.dp)))
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
        Text(BidiText.isolateLtr(value.toString()), style = CoineProTextStyles.RowFigure, color = colour)
        Text(
            stringResource(label),
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
    /** The second action, where a section has one. Null draws a single action. */
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
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
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) {
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }
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
private fun Muted(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = CoineProSpacing.One),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
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

/* ----------------------------------------------------------- translations */

@Composable
private fun String.asDisplayValue(): String = when (this) {
    HUB_ON -> stringResource(R.string.admin_on)
    com.coinepro.core.diagnostics.HUB_OFF -> stringResource(R.string.admin_off)
    else -> BidiText.isolateLtr(this)
}

@Composable
private fun MarketPlatform.label(): String = stringResource(
    when (this) {
        MarketPlatform.COINEPRO_FX -> R.string.admin_platform_forex
        MarketPlatform.TRADEYAR -> R.string.admin_platform_crypto
    },
)

private fun HubSection.labelRes(): Int = when (this) {
    HubSection.SESSION -> R.string.admin_tile_session
    HubSection.FEED -> R.string.admin_tile_feed
    HubSection.PUSH -> R.string.admin_tile_push
    HubSection.VENUE -> R.string.admin_tile_venue
    HubSection.ROUTES -> R.string.admin_tile_routes
    HubSection.REQUESTS -> R.string.admin_tile_requests
}

@DrawableRes
private fun HubSection.icon(): Int = when (this) {
    HubSection.SESSION -> CoineProIcons.Locked
    HubSection.FEED -> CoineProIcons.TrendUp
    HubSection.PUSH -> CoineProIcons.Activity
    HubSection.VENUE -> CoineProIcons.Wallet
    HubSection.ROUTES -> CoineProIcons.Link
    HubSection.REQUESTS -> CoineProIcons.Refresh
}

@Composable
private fun HubTone.colour(): Color = when (this) {
    HubTone.GOOD -> CoineProColors.Buy
    HubTone.WARN -> CoineProColors.Warning
    HubTone.BAD -> CoineProColors.Sell
    HubTone.IDLE -> CoineProColors.TextMuted
}

@StringRes
private fun PushPermission.labelRes(): Int = when (this) {
    PushPermission.NOT_CONFIGURED -> R.string.admin_push_not_configured
    PushPermission.NOT_REQUIRED -> R.string.admin_push_not_required
    PushPermission.AVAILABLE -> R.string.admin_push_available
    PushPermission.DENIED -> R.string.admin_push_denied
    PushPermission.GRANTED -> R.string.admin_push_granted
}

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
