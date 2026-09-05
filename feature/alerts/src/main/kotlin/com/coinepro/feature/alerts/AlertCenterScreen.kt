package com.coinepro.feature.alerts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProConfirmDialog
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProHeaderAction
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.CoineProToast
import com.coinepro.core.designsystem.LocalToaster
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.designsystem.rowMotion
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.symbols.SymbolArtwork
import java.time.Instant

/**
 * The alert centre: the list, and the two sheets it opens.
 *
 * ### What the list is for
 *
 * Somebody with eleven alerts opens this screen to answer one of two questions — "what am I still
 * waiting for" or "did the one I care about go off". Both are answered by the headings rather than
 * by reading rows, which is why the list is cut into three by [AlertGrouping] instead of sorted.
 *
 * ### The sentence is the content
 *
 * Each row is one Persian sentence — «BTC/USDT بالای 68,500» — at reading weight, and everything
 * else on the row is muted and half a size smaller. The alternative, and what the previous alerts
 * screen did, is a row of labelled fields the reader reassembles into that sentence themselves,
 * eleven times a screen.
 *
 * ### One action, and one question
 *
 * The only primary action here is «هشدار تازه». Pause, edit, duplicate and delete live behind a
 * long press, because they are things done to a particular alert rather than things the screen
 * offers. Of those four only delete asks first; the rest are undone from the toaster, which is
 * cheaper for the reader than a dialog in front of every reversible act.
 *
 * Both sets are declared in [AlertCenterActions] rather than in the source of the composables that
 * draw them, and pinned by a test. A menu whose order changes between releases is the most repeated
 * complaint in this market's negative reviews, and an order that lives only in the layout is one
 * nothing can hold on to.
 *
 * ### One list, two venues
 *
 * Device alerts and server alerts are in the same three sections, grouped by the same rules, and a
 * server one carries a mark. The app used to have two alert screens with two models; see
 * [AlertVenue] for why that was a bug rather than two features.
 */
@Composable
fun AlertCenterScreen(controller: AlertsController, initialSymbol: String? = null) {
    val state by controller.state.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val undoLabel = stringResource(R.string.alerts_undo)
    val pausedNote = stringResource(R.string.alerts_paused_done)
    val resumedNote = stringResource(R.string.alerts_resumed_done)
    val duplicatedNote = stringResource(R.string.alerts_duplicated_done)

    // Arriving from a chart or a market row is an instruction, not a visit: the reader pressed
    // "alert me about this", so the editor opens on that instrument rather than making them find
    // it again in a picker.
    LaunchedEffect(initialSymbol) {
        if (!initialSymbol.isNullOrBlank()) controller.openEditor(initialSymbol)
    }

    Column(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
        CoineProListHeader(
            title = stringResource(R.string.alerts_centre_title),
            subtitle = if (state.total == 0) null else state.subtitleText(),
            actions = {
                // Two, in `AlertCenterActions.PRIMARY`'s order and no other. See that file.
                CoineProHeaderAction(
                    icon = CoineProIcons.Add,
                    label = stringResource(R.string.alerts_new),
                    onClick = { controller.openEditor() },
                )
                CoineProHeaderAction(
                    icon = CoineProIcons.Link,
                    label = stringResource(R.string.webhooks_title),
                    onClick = controller::openWebhooks,
                )
            },
        )
        CoineProTeachingStrip(TeachingSurface.ALERTS)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                bottom = CoineProSpacing.Six,
            ),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            if (state.full) {
                item(key = "full") { FullNotice() }
            }

            if (state.empty) {
                item(key = "empty") {
                    CoineProEmptyState(
                        message = stringResource(R.string.alerts_centre_empty),
                        icon = CoineProIcons.Bell,
                        hint = stringResource(R.string.alerts_centre_empty_hint),
                        action = stringResource(R.string.alerts_centre_empty_action),
                        onAction = { controller.openEditor() },
                        // An invitation, not a retry: this is the only thing on the page worth
                        // doing and the reason the reader opened it.
                        actionIsPrimary = true,
                        modifier = Modifier.padding(top = CoineProSpacing.Four),
                    )
                }
            }

            state.sections.forEach { section ->
                item(key = "header-${section.kind.name}") {
                    SectionHeader(kind = section.kind, count = section.rows.size)
                }
                items(section.rows, key = { it.alert.id }) { row ->
                    Column(modifier = rowMotion().fillMaxWidth()) {
                        AlertListRow(
                            row = row,
                            onOpen = { controller.openAudit(row) },
                            onActions = { controller.openActions(row) },
                        )
                    }
                }
            }
        }
    }

    state.actionsFor?.let { row ->
        AlertActionsSheet(
            row = row,
            // Hidden rather than dim at the cap. A copy the store would refuse is not an action.
            canDuplicate = !state.full,
            onDismiss = controller::closeActions,
            onPause = {
                val paused = row.alert.active
                controller.setPaused(row, paused)
                toaster.show(
                    CoineProToast(
                        message = if (paused) pausedNote else resumedNote,
                        actionLabel = undoLabel,
                        onAction = controller::undo,
                    ),
                )
            },
            onEdit = { controller.editAlert(row) },
            onDuplicate = {
                controller.duplicate(row)
                toaster.show(
                    CoineProToast(
                        message = duplicatedNote,
                        actionLabel = undoLabel,
                        onAction = controller::undo,
                    ),
                )
            },
            onDelete = { controller.requestDelete(row) },
            onHistory = { controller.openAudit(row) },
        )
    }

    state.confirmingDelete?.let { row ->
        CoineProConfirmDialog(
            title = stringResource(R.string.alerts_delete_title),
            message = row.sentence + "\n" + stringResource(R.string.alerts_delete_body),
            confirmLabel = stringResource(R.string.alerts_delete_confirm),
            dismissLabel = stringResource(R.string.alerts_delete_cancel),
            destructive = true,
            onConfirm = controller::confirmDelete,
            onDismiss = controller::cancelDelete,
        )
    }

    state.draft?.let { draft ->
        AlertEditorSheet(
            draft = draft,
            matches = state.symbolMatches,
            refusal = state.refusal,
            controller = controller,
        )
    }

    state.audit?.let { view ->
        AlertAuditSheet(view = view, onDismiss = controller::closeAudit)
    }

    if (state.webhooksOpen) {
        WebhookSheet(
            targets = state.webhookTargets,
            draft = state.webhookDraft,
            test = state.webhookTest,
            controller = controller,
        )
    }
}

/**
 * «۳ فعال از ۱۱» — a prose count, so the digits are Persian.
 *
 * The one place on this screen where they are. Everything else here is a market figure and stays
 * Latin; this is a sentence about how many things there are.
 */
@Composable
private fun AlertsUiState.subtitleText(): String {
    val armed = sections.firstOrNull { it.kind == AlertSectionKind.ARMED }
        ?.rows
        ?.count { !it.paused }
        ?: 0
    return stringResource(
        R.string.alerts_centre_subtitle,
        armed.toPersianDigits(),
        total.toPersianDigits(),
    )
}

/** Said before the reader fills a form in, not after they press save on one. */
@Composable
private fun FullNotice() {
    CoineProCard(modifier = Modifier.fillMaxWidth(), accent = CoineProColors.Warning) {
        Text(
            text = stringResource(R.string.alerts_full),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
    }
}

/**
 * A section's name and how many are under it.
 *
 * Deliberately smaller than [CoineProListHeader]: it separates the list rather than introducing it,
 * and at title weight three of them would out-shout the rows they are labelling.
 */
@Composable
private fun SectionHeader(kind: AlertSectionKind, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CoineProSpacing.OneHalf, bottom = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(kind.titleRes()),
            style = MaterialTheme.typography.labelSmall,
            color = kind.ink(),
        )
        Text(
            text = count.toPersianDigits(),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/**
 * One alert.
 *
 * A tap opens the history rather than the editor. That is the deliberate choice on this screen: the
 * question a reader has about an alert they can already read in full on its own row is almost never
 * "what does it say" — it is "did it work", and the history is the only place that answers it.
 * Changing the alert is a long press away, with everything else that changes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlertListRow(row: AlertRow, onOpen: () -> Unit, onActions: () -> Unit) {
    CoineProCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onActions),
        contentPadding = PaddingValues(
            horizontal = CoineProSpacing.CardHorizontal,
            vertical = CoineProSpacing.OneHalf,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlertMark(scope = row.alert.effectiveScope)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.sentence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.paused) CoineProColors.TextMuted else CoineProColors.TextPrimary,
                    // Right, never End. The sentence starts with a Latin ticker, and an End
                    // alignment resolved against the paragraph's own direction has already put one
                    // of these on the wrong edge once.
                    textAlign = TextAlign.Right,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = row.metaLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (row.venue == AlertVenue.SERVER) {
                VenuePill()
            }
            if (row.paused) {
                PausedPill()
            }
        }
    }
}

/**
 * «H1 · یک‌بار» — everything about the alert that is not its condition.
 *
 * The timeframe is dropped rather than shown as a dash where nothing knows one; a placeholder in a
 * secondary line is noise the reader has to learn to ignore. In the fired section the moment it
 * went off is appended, because that section exists to answer *when*.
 */
private fun AlertRow.metaLine(): String {
    val parts = buildList {
        timeframe?.takeIf(String::isNotBlank)?.let { add(BidiText.isolateLtr(it)) }
        // An alert with no bar policy is governed by the older wall-clock repeat, whose common
        // case is a one-shot and reads as the same word. Better one honest word than a blank.
        add(AlertVocabulary.frequency(alert.frequency ?: AlertFrequency.ONCE))
        if (kind == AlertSectionKind.FIRED) {
            alert.lastFiredAtEpochMillis?.let { add(PersianDateTime.moment(Instant.ofEpochMilli(it))) }
        }
    }
    return parts.joinToString(" · ")
}

/**
 * The disc at the leading edge of a row.
 *
 * A watchlist alert has no one instrument, so it gets the screen's own glyph rather than the logo
 * of whichever symbol happens to be first in the list. A symbol with no artwork gets the same
 * treatment: this app does not put a lettered disc in a list, and the picker never offers one — the
 * guard is here because an alert can outlive the catalogue entry it was made from.
 */
@Composable
private fun AlertMark(scope: AlertScope, size: Dp = 34.dp) {
    val ticker = (scope as? AlertScope.Symbol)?.ticker
    if (ticker != null && SymbolArtwork.covers(ticker)) {
        CoineProAssetLogo(symbol = ticker, size = size)
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CoineProTint.fill(CoineProColors.TextMuted, CoineProColors.Surface)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(CoineProIcons.Bell),
            contentDescription = null,
            modifier = Modifier.size(size / 2),
            tint = CoineProColors.TextMuted,
        )
    }
}

/**
 * The mark on a row the server decides.
 *
 * Only on the server rows. A badge on both venues would be two badges on every row saying nothing,
 * and the device alert is what this app is: the marked case is the exception. What the reader gets
 * from it is the answer to the question they ask when an alert did not arrive — whether the silence
 * could have been this phone being asleep — and the editor spells that out when the alert is made.
 */
@Composable
private fun VenuePill() {
    Text(
        text = stringResource(R.string.alerts_venue_badge),
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextSecondary,
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(CoineProColors.SurfaceElevated)
            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
    )
}

/** The mark on a row the reader switched off. A word, because a dimmed row alone is ambiguous. */
@Composable
private fun PausedPill() {
    Text(
        text = stringResource(R.string.alerts_paused),
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(CoineProColors.SurfaceElevated)
            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
    )
}

/**
 * The long-press menu.
 *
 * Its contents and their order are [AlertCenterActions]', not this composable's — that is the whole
 * point of that file, and the reason it exists is in its own KDoc. What is decided here is only how
 * each row is drawn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertActionsSheet(
    row: AlertRow,
    canDuplicate: Boolean,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit,
) {
    CoineProSheet(
        title = stringResource(R.string.alerts_actions_title),
        subtitle = row.sentence,
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(bottom = CoineProSpacing.Two)) {
            // Rendered from `AlertCenterActions`, in its order, rather than written out here. The
            // order of this menu is a promise to somebody's thumb — see that file for the review
            // evidence — and a list written out in a composable is an order nothing can assert.
            AlertCenterActions.forRow(row = row, canDuplicate = canDuplicate).forEach { action ->
                ActionRow(
                    label = stringResource(action.labelRes(paused = !row.alert.active)),
                    onClick = when (action) {
                        AlertCenterAction.HISTORY -> onHistory
                        AlertCenterAction.PAUSE -> onPause
                        AlertCenterAction.EDIT -> onEdit
                        AlertCenterAction.DUPLICATE -> onDuplicate
                        AlertCenterAction.DELETE -> onDelete
                    },
                    destructive = action == AlertCenterAction.DELETE,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (destructive) CoineProColors.Sell else CoineProColors.TextPrimary,
        textAlign = TextAlign.Right,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
    )
}

/**
 * What each action is called.
 *
 * «توقف» and «ادامه» are the same action with two labels, which is why this takes the alert's state
 * rather than being a property of the enum: the menu offers one row there, and whether it says stop
 * or resume is a fact about this alert rather than about the menu.
 */
private fun AlertCenterAction.labelRes(paused: Boolean): Int = when (this) {
    AlertCenterAction.HISTORY -> R.string.alerts_action_history
    AlertCenterAction.PAUSE ->
        if (paused) R.string.alerts_action_resume else R.string.alerts_action_pause
    AlertCenterAction.EDIT -> R.string.alerts_action_edit
    AlertCenterAction.DUPLICATE -> R.string.alerts_action_duplicate
    AlertCenterAction.DELETE -> R.string.alerts_action_delete
}

/** The heading each section carries. */
private fun AlertSectionKind.titleRes(): Int = when (this) {
    AlertSectionKind.ARMED -> R.string.alerts_section_armed
    AlertSectionKind.FIRED -> R.string.alerts_section_fired
    AlertSectionKind.EXPIRED -> R.string.alerts_section_expired
}

/**
 * The one place colour is spent on this screen.
 *
 * «تازه اجرا شده» is the section somebody opened the app for, so its heading carries the accent and
 * the other two do not. Colouring all three would be colouring none.
 */
@Composable
private fun AlertSectionKind.ink() = when (this) {
    AlertSectionKind.ARMED -> CoineProColors.TextSecondary
    AlertSectionKind.FIRED -> CoineProColors.Gold
    AlertSectionKind.EXPIRED -> CoineProColors.TextMuted
}
