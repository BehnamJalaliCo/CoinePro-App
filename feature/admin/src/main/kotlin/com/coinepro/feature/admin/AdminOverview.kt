package com.coinepro.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.diagnostics.ABSENT
import com.coinepro.core.diagnostics.AdminUiState
import com.coinepro.core.diagnostics.Crash
import com.coinepro.core.diagnostics.CrashReport
import com.coinepro.core.diagnostics.ControlHub
import com.coinepro.core.diagnostics.FeedStatus
import com.coinepro.core.diagnostics.FindingKind
import com.coinepro.core.diagnostics.HealthFinding
import com.coinepro.core.diagnostics.HUB_ON
import com.coinepro.core.diagnostics.HUB_OFF
import com.coinepro.core.diagnostics.HubActions
import com.coinepro.core.diagnostics.HubSection
import com.coinepro.core.diagnostics.HubTile
import com.coinepro.core.diagnostics.LogCounters
import com.coinepro.core.diagnostics.SessionRow
import com.coinepro.core.diagnostics.ServerCapabilities
import com.coinepro.core.diagnostics.RelayStatus
import com.coinepro.core.diagnostics.VenueStatus
import com.coinepro.core.diagnostics.hubTiles
import com.coinepro.core.diagnostics.tone
import com.coinepro.core.diagnostics.verdictTone
import com.coinepro.core.model.MarketPlatform
import java.time.Instant

/**
 * The section that answers the question the panel is opened with: is anything wrong.
 *
 * It leads with a verdict, and the verdict is the part that was rebuilt rather than restyled. The
 * old one said "خطا دارد" on any install that had not signed in, because it counted every non-2xx
 * status — including the 401 a signed-out session gets from every authenticated route. An operator
 * cannot debug from a panel that is already claiming a fault before they have touched anything, so
 * the verdict now names findings that are specifically actionable, and says in a line underneath
 * why a refusal is not one of them.
 */
internal fun LazyListScope.overviewSection(
    state: AdminUiState,
    hub: ControlHub,
    actions: HubActions,
    findings: List<HealthFinding>,
    counters: LogCounters,
) {
    item { Verdict(findings) }
    item { Counters(state, counters) }
    item { TileGrid(state.hubTiles(hub)) }

    hub.sessions.takeIf { it.isNotEmpty() }?.let { sessions ->
        item { SessionCard(sessions, state.selected, actions) }
    }
    hub.feed?.let { item { FeedCard(it, actions) } }
    hub.venue?.let { item { VenueCard(it) } }
    hub.relay?.let { item { RelayCard(it) } }
    hub.capabilities[state.selected]?.let {
        item { CapabilitiesCard(it, state.selected, actions) }
    }
    item { CrashCard(state.crash, actions) }
}

@Composable
private fun Verdict(findings: List<HealthFinding>) {
    val tone = findings.verdictTone()
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
                if (findings.isEmpty()) R.string.admin_verdict_healthy else R.string.admin_verdict_faulty,
            ),
            style = CoineProTextStyles.Balance,
            color = tone.colour(),
        )
        Spacer(Modifier.height(6.dp))
        if (findings.isEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(CoineProIcons.Success),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = CoineProColors.Buy,
                )
                Text(
                    text = stringResource(R.string.admin_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        } else {
            // Each finding is a sentence an operator can act on, with how many times it happened.
            // A single red word with nothing under it is what made the old verdict unreadable.
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                findings.forEachIndexed { index, finding ->
                    if (index > 0) Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
                        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusRail(finding.kind.tone().colour())
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(finding.kind.labelRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = CoineProColors.TextPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.admin_finding_count, count(finding.count)),
                                style = MaterialTheme.typography.bodySmall,
                                color = CoineProColors.TextMuted,
                            )
                        }
                    }
                }
            }
        }
        Muted(stringResource(R.string.admin_verdict_note))
    }
}

/**
 * Four numbers, read rather than accumulated.
 *
 * They sit above the tiles because they are the coarsest reading there is: an operator who sees
 * zero errors and zero failed calls can stop looking, and one who sees eleven knows which section
 * to open before they have scrolled.
 */
@Composable
private fun Counters(state: AdminUiState, counters: LogCounters) {
    val failed = state.requests.count {
        it.platform == state.selected && it.failed && it.status != 401 && it.status != 403
    }
    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        Tally(
            Modifier.weight(1f),
            stringResource(R.string.admin_counter_errors),
            count(counters.errors),
            if (counters.errors > 0) CoineProColors.Sell else CoineProColors.TextMuted,
        )
        Tally(
            Modifier.weight(1f),
            stringResource(R.string.admin_counter_warnings),
            count(counters.warnings),
            if (counters.warnings > 0) CoineProColors.Warning else CoineProColors.TextMuted,
        )
        Tally(
            Modifier.weight(1f),
            stringResource(R.string.admin_counter_failures),
            count(failed),
            if (failed > 0) CoineProColors.Warning else CoineProColors.TextMuted,
        )
        Tally(
            Modifier.weight(1f),
            stringResource(R.string.admin_counter_lines),
            count(counters.total),
            CoineProColors.TextSecondary,
        )
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
        Text(text = tile.value.asDisplayValue(), style = CoineProTextStyles.RowFigure, color = colour)
        Text(
            text = stringResource(tile.section.labelRes()),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

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
                Spacer(Modifier.size(CoineProSpacing.One))
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
        // A count of symbols, not a market figure — the counter tiles above this card already
        // print «۱» and «۰», and one card down the page saying «8» made the same screen read in
        // two number systems.
        Field(R.string.admin_feed_symbols, count(feed.subscribedSymbols))
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

/**
 * The exchange's own price relay, which is not this app's feed and fails apart from it.
 *
 * Every number is printed and none is interpreted except the state line, because that is what an
 * operator needs to hand to the server's team: `ws 5/5, 3816ms` is a sentence they can act on and
 * a green light is not. The age carries its own note — see [RelayStatus.tickAgeMillis] — since an
 * operator who reads four seconds as a fault will file a bug against a healthy relay.
 */
@Composable
private fun RelayCard(relay: RelayStatus) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.TrendUp, R.string.admin_relay_title)
        Field(R.string.admin_relay_tier, figure(relay.tier), relay.tone.colour())
        Field(
            R.string.admin_relay_sockets,
            figure((relay.socketsUp?.toString() ?: ABSENT) + " / " + (relay.socketsTotal?.toString() ?: ABSENT)),
            // The half a single flag misses: the relay still calls itself connected with one
            // shard alive out of five, and four fifths of the catalogue frozen behind it.
            if (relay.degraded) CoineProColors.Warning else CoineProColors.Buy,
        )
        Field(R.string.admin_relay_tick_age, figure(relay.tickAgeMillis?.let { it.toString() + "ms" } ?: ABSENT))
        Muted(stringResource(R.string.admin_relay_note))
    }
}

@Composable
private fun VenueCard(venue: VenueStatus) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Wallet, R.string.admin_venue_title)
        Field(R.string.admin_venue_name, figure(venue.name))
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
            capabilities.symbolCount?.let { count(it) } ?: ABSENT,
        )
        CoineProSecondaryButton(
            text = stringResource(R.string.admin_capabilities_refresh),
            onClick = { actions.onRefreshCapabilities(platform) },
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
        Muted(stringResource(R.string.admin_capabilities_note))
    }
}

/**
 * The last uncaught exception, with the log tail that led to it already in the file.
 *
 * On the overview rather than buried, because a crash outranks everything else this panel can find:
 * whatever else is wrong, an app that died is what somebody is here about.
 */
@Composable
private fun CrashCard(crash: Crash?, actions: HubActions) {
    val context = LocalContext.current
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Warning, R.string.admin_crash_title)
        if (crash == null) {
            Muted(stringResource(R.string.admin_crash_none))
            return@CoineProCard
        }
        Field(R.string.admin_crash_at, PersianDateTime.moment(Instant.ofEpochMilli(crash.atEpochMillis)))
        Muted(figure(crash.summary))
        crash.culprit?.let { Field(R.string.admin_crash_where, figure(it)) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            CoineProSecondaryButton(
                text = stringResource(R.string.admin_crash_copy),
                onClick = { DiagnosticHandoff.copy(context, "CoinePro crash", crash.trace) },
                modifier = Modifier.weight(1f),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.admin_crash_clear),
                onClick = {
                    // The file and the state, in that order. Clearing only the state would put the
                    // crash back on screen the next time the panel captured it, which reads as the
                    // button not working.
                    CrashReport(context).clear()
                    actions.onClearCrash()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Capability(label: Int, value: Boolean?) {
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
            // "Off" and "never asked" both read muted, and the words are what tell them apart.
            // Colouring "unknown" would be the panel guessing on the server's behalf.
            else -> CoineProColors.TextMuted
        },
    )
}

@Composable
private fun String.asDisplayValue(): String = when (this) {
    HUB_ON -> stringResource(R.string.admin_on)
    HUB_OFF -> stringResource(R.string.admin_off)
    else -> figure(this)
}

private fun HubSection.labelRes(): Int = when (this) {
    HubSection.SESSION -> R.string.admin_tile_session
    HubSection.FEED -> R.string.admin_tile_feed
    HubSection.PUSH -> R.string.admin_tile_push
    HubSection.VENUE -> R.string.admin_tile_venue
    HubSection.ROUTES -> R.string.admin_tile_routes
    HubSection.REQUESTS -> R.string.admin_tile_requests
}

private fun HubSection.icon(): Int = when (this) {
    HubSection.SESSION -> CoineProIcons.Locked
    HubSection.FEED -> CoineProIcons.TrendUp
    HubSection.PUSH -> CoineProIcons.Activity
    HubSection.VENUE -> CoineProIcons.Wallet
    HubSection.ROUTES -> CoineProIcons.Link
    HubSection.REQUESTS -> CoineProIcons.Refresh
}

private fun FindingKind.labelRes(): Int = when (this) {
    FindingKind.CRASH -> R.string.admin_finding_crash
    FindingKind.ROUTE_MISSING -> R.string.admin_finding_route
    FindingKind.SERVER_ERROR -> R.string.admin_finding_server
    FindingKind.TRANSPORT_FAILURE -> R.string.admin_finding_transport
    FindingKind.GATEWAY_UNCONFIGURED -> R.string.admin_finding_gateway
    FindingKind.ERRORS_LOGGED -> R.string.admin_finding_errors
}
