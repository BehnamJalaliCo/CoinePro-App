package com.coinepro.feature.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.diagnostics.AdminUiState
import com.coinepro.core.diagnostics.DiagnosticExport
import com.coinepro.core.diagnostics.ExportOutcome
import com.coinepro.core.diagnostics.HubActions
import com.coinepro.core.diagnostics.LogEntry
import com.coinepro.core.diagnostics.LogLevel
import com.coinepro.core.diagnostics.LogTag
import com.coinepro.core.diagnostics.LogWindow

/**
 * The log, its filters, and the file.
 *
 * ### What the owner asked for, in one section
 *
 * "One professional system-wide log so an operator can see an error and fix it", and "a professional
 * logging system so I can export the output and hand it to you or to any developer". Both halves are
 * here, and the second is the harder one: the export is what makes the log leave the phone, and a
 * log that cannot leave the phone is a log only the person holding it can read.
 *
 * ### Why the filter bar is above the list and not in a sheet
 *
 * Because the filter is the reading. An operator narrows to WARN, to NETWORK, to the last five
 * minutes, sees three lines, and exports exactly those three lines — and the file says at the top
 * which filter produced it, so it can never look truncated. Hiding the controls behind a button
 * would put one tap between the operator and the only thing that makes six hundred lines usable.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.logSection(
    state: AdminUiState,
    actions: HubActions,
    visible: List<LogEntry>,
    report: () -> String,
    logText: () -> String,
) {
    item {
        SectionHeader(
            title = stringResource(R.string.admin_log_title),
            detail = stringResource(R.string.admin_log_count, count(visible.size), count(state.log.size)),
            actionLabel = stringResource(R.string.admin_log_clear_filter)
                .takeIf { state.filter.active },
            onAction = actions.onClearFilter.takeIf { state.filter.active },
        )
    }

    item { FilterCard(state, actions) }
    item { ExportCard(state, actions, report, logText) }

    when {
        state.log.isEmpty() -> item { EmptyNote(R.string.admin_log_empty) }
        visible.isEmpty() -> item { EmptyNote(R.string.admin_log_empty_filtered) }
        else -> item {
            // Capped, because a `LazyColumn` item is composed whole: six hundred rows inside one
            // item is six hundred rows built on the frame the section scrolls into view.
            Rows(visible.size) { index ->
                LogRow(
                    entry = visible[index],
                    expanded = state.expandedEntry == visible[index].sequence,
                    onClick = { actions.onExpandEntry(visible[index].sequence) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterCard(state: AdminUiState, actions: HubActions) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Filter, R.string.admin_log_tags)

        CoineProTextField(
            value = state.filter.query,
            onValueChange = actions.onSetQuery,
            label = stringResource(R.string.admin_log_search),
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )

        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        Label(R.string.admin_log_level)
        // The level names stay Latin. They are the letters the exported file uses, and an operator
        // comparing the screen against the file should not have to translate between two alphabets.
        CoineProSegmentedControl(
            options = LogLevel.entries.map { it to it.name },
            selected = state.filter.minimumLevel,
            onSelect = actions.onSetMinimumLevel,
        )

        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        Label(R.string.admin_log_window)
        CoineProSegmentedControl(
            options = LogWindow.entries.map { it to stringResource(it.labelRes()) },
            selected = state.filter.window,
            onSelect = actions.onSetWindow,
        )

        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        Label(R.string.admin_log_tags)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogTag.entries.forEach { tag ->
                FilterChip(
                    label = tag.label(),
                    selected = tag in state.filter.tags,
                    onClick = { actions.onToggleTag(tag) },
                )
            }
        }
    }
}

/**
 * The three ways the report leaves the phone.
 *
 * Share and save are genuinely different acts rather than one with two names — see
 * [DiagnosticHandoff]. Copy is the third and the smallest: no file, no picker, straight into a
 * message box.
 *
 * The outcome line matters more than it looks. Android stopped showing its own "copied"
 * confirmation in 13, and a share sheet that fails to open leaves nothing behind at all; without a
 * line saying what happened, an operator presses the button three more times.
 */
@Composable
private fun ExportCard(
    state: AdminUiState,
    actions: HubActions,
    report: () -> String,
    logText: () -> String,
) {
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DiagnosticExport.MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        actions.onExported(DiagnosticHandoff.save(context, uri, report()))
    }

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Copy, R.string.admin_export_title)
        Muted(stringResource(R.string.admin_export_body))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            CoineProSecondaryButton(
                text = stringResource(R.string.admin_export_share),
                onClick = {
                    actions.onExported(
                        DiagnosticHandoff.share(context, report(), System.currentTimeMillis()),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.admin_export_save),
                onClick = { save.launch(DiagnosticExport.fileName(System.currentTimeMillis())) },
                modifier = Modifier.weight(1f),
            )
        }
        CoineProSecondaryButton(
            text = stringResource(R.string.admin_export_copy),
            onClick = {
                val copied = DiagnosticHandoff.copy(context, "CoinePro log", logText())
                actions.onExported(if (copied) ExportOutcome.SAVED else ExportOutcome.FAILED)
            },
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )

        when (state.exportOutcome) {
            ExportOutcome.SHARED -> Outcome(R.string.admin_export_done_share, CoineProColors.Buy)
            ExportOutcome.SAVED -> Outcome(R.string.admin_export_done_save, CoineProColors.Buy)
            ExportOutcome.FAILED -> Outcome(R.string.admin_export_failed, CoineProColors.Sell)
            ExportOutcome.NONE -> Unit
        }
        Muted(stringResource(R.string.admin_export_redaction))
    }
}

/**
 * One log line, and its fields when it is opened.
 *
 * Collapsed it is one line: a coloured level letter, the clock, the tag and the message. At this
 * density the eye is scanning for the red ones, and a spelled-out «هشدار» on every row would push
 * the message off the edge.
 *
 * Opened it shows the fields, the thread and the sequence number — which is what turns "this call
 * 401ed" into a line somebody can act on — and a control that copies that single entry. Copying one
 * line is what an operator wants nine times out of ten; the whole file is for the tenth.
 */
@Composable
private fun LogRow(entry: LogEntry, expanded: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = entry.level.initial(),
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                color = entry.level.colour(),
            )
            Text(
                // The rendered line without its timestamp prefix: the panel is being read now, and
                // the wall clock in front of every row would cost half the width for a value the
                // exported file carries anyway.
                text = figure(entry.render().substringAfter(' ')),
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                color = if (entry.level == LogLevel.ERROR) {
                    CoineProColors.TextPrimary
                } else {
                    CoineProColors.TextSecondary
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (!expanded) return@Column

        Spacer(Modifier.height(CoineProSpacing.One))
        Field(R.string.admin_log_sequence, figure(entry.sequence))
        Field(R.string.admin_log_thread, figure(entry.thread))
        entry.fields.forEach { (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = figure(key),
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                    color = CoineProColors.TextMuted,
                )
                Text(
                    text = figure(value),
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                    color = CoineProColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        CoineProSecondaryButton(
            text = stringResource(R.string.admin_log_copy_entry),
            onClick = { DiagnosticHandoff.copy(context, "CoinePro log line", entry.render()) },
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
    }
}

@Composable
private fun Label(label: Int) {
    Text(
        text = stringResource(label),
        modifier = Modifier.padding(bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
}

@Composable
private fun Outcome(text: Int, colour: Color) {
    Text(
        text = stringResource(text),
        modifier = Modifier.padding(top = CoineProSpacing.One),
        style = MaterialTheme.typography.bodySmall,
        color = colour,
    )
}
