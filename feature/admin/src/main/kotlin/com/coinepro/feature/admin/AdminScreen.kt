package com.coinepro.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.diagnostics.AdminSection
import com.coinepro.core.diagnostics.AdminUiState
import com.coinepro.core.diagnostics.ControlHub
import com.coinepro.core.diagnostics.CrashReport
import com.coinepro.core.diagnostics.DeviceReport
import com.coinepro.core.diagnostics.HubActions
import com.coinepro.core.diagnostics.counters
import com.coinepro.core.diagnostics.findings
import com.coinepro.core.diagnostics.visibleLog

/**
 * The admin panel.
 *
 * ### What it is for
 *
 * The product's real admin panels live on each server, where the data and the authority are. This
 * one owns the half neither of them can reach: the app on this handset. Four sections, in the order
 * an operator uses them — *is anything wrong*, *show me the log and give me the file*, *what did we
 * say to which server*, *what is this build running on*.
 *
 * ### What was wrong with the one this replaces
 *
 * Three things, all named by the owner and all structural rather than cosmetic.
 *
 * It **opened onto its own error state**: the verdict counted every non-2xx status as a failure, so
 * the 401 that a signed-out session gets from every authenticated route made the panel claim a
 * fault before anybody had touched it. Nobody can debug from a screen that is already lying, and
 * after the second time nobody reads it. The verdict now comes from
 * [com.coinepro.core.diagnostics.findings], which counts only what is actually actionable.
 *
 * It **could not produce a file**. The log had a copy button and nothing else, so the one thing the
 * owner actually asked for — hand the output to a developer — was a clipboard paste with no build,
 * no device, no state and no crash around it. The export is now a report rather than a dump.
 *
 * It **opened to anybody**. Five taps on a version number is a way of *finding* a panel, not a way
 * of guarding one, and this panel signs sessions out and drops caches. It is now behind
 * [AdminLockScreen].
 *
 * ### Everything is scoped to the platform in the switch
 *
 * Unchanged, and it matters more here than anywhere else: the two backends are separate accounts on
 * separate servers, and a panel that merged them would be the one screen in the product where they
 * look like a single system.
 */
@Composable
fun AdminScreen(
    state: AdminUiState,
    hub: ControlHub = ControlHub(),
    actions: HubActions = HubActions(),
    /**
     * The whole report, built on demand.
     *
     * A lambda rather than a string because building it walks six hundred log entries and every
     * request, and doing that on every recomposition of a screen with a text field on it would cost
     * the panel its own responsiveness. It is called when a button is pressed and not before.
     */
    report: () -> String = { "" },
    /** The filtered log as text, for the clipboard. Same reasoning as [report]. */
    logText: () -> String = { "" },
) {
    if (!state.gate.unlocked) {
        // Read once per composition rather than ticked: the lockout message is measured in minutes
        // and a countdown that repainted every second would be a panel animating at a reader who is
        // locked out of it.
        AdminLockScreen(state.gate, actions, remember { System.currentTimeMillis() })
        return
    }

    // Read once per visit rather than watched. A crash file cannot change while the app that
    // would write it is the one on screen, and a memory reading taken when the panel opened is the
    // reading the operator is looking at. Both need a Context, which is why the screen captures
    // them and hands them down rather than the controller reaching for one.
    val context = LocalContext.current
    LaunchedEffect(context) {
        actions.onObserveInstall(DeviceReport.capture(context), CrashReport(context).last())
    }

    // One clock for the whole frame. Two readings — the verdict's window and the log filter's —
    // taken a millisecond apart would let a section disagree with the counter above it.
    val now = remember(state.log.size, state.requests.size) { System.currentTimeMillis() }
    val findings = state.findings(now)
    val counters = state.log.counters(now)

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
        item {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                Text(
                    text = stringResource(R.string.admin_lock_title),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
                CoineProSegmentedControl(
                    options = AdminSection.entries.map { it to stringResource(it.labelRes()) },
                    selected = state.section,
                    onSelect = actions.onShowSection,
                )
                // The platform switch stays under the section switch rather than beside it. It
                // changes which account and which server every reading below refers to, which is a
                // heavier thing than choosing a tab, and stacking them says so.
                CoineProSegmentedControl(
                    options = state.panels.keys.toList().map { it to it.label() },
                    selected = state.selected,
                    onSelect = actions.onSelectPlatform,
                )
            }
        }

        when (state.section) {
            AdminSection.OVERVIEW -> overviewSection(state, hub, actions, findings, counters)
            AdminSection.LOG -> logSection(
                state = state,
                actions = actions,
                visible = state.visibleLog(now, LOG_ROWS),
                report = report,
                logText = logText,
            )
            AdminSection.NETWORK -> networkSection(state, actions)
            AdminSection.SYSTEM -> systemSection(state, actions, hub.push, state.verbosity)
        }
    }
}

private fun AdminSection.labelRes(): Int = when (this) {
    AdminSection.OVERVIEW -> R.string.admin_section_overview
    AdminSection.LOG -> R.string.admin_section_log
    AdminSection.NETWORK -> R.string.admin_section_network
    AdminSection.SYSTEM -> R.string.admin_section_system
}

/**
 * How many log lines the screen composes.
 *
 * The ring holds six hundred and the export gets every one that matches the filter; this is what is
 * *drawn*, and it is capped because a `LazyColumn` item is composed whole — six hundred rows inside
 * one item is six hundred rows built on the frame the section scrolls into view. Narrowing the
 * filter is how an operator reaches past it, which is also how they would want to read it.
 */
private const val LOG_ROWS = 150
