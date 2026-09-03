package com.coinepro.feature.search

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistColumn
import com.coinepro.core.datastore.WatchlistFlag
import com.coinepro.core.datastore.WatchlistImport
import com.coinepro.core.datastore.WatchlistSettings
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProConfirmDialog
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.rememberCoineProHaptics
import kotlinx.coroutines.launch

/**
 * Every panel the watchlist opens over itself.
 *
 * One entry point rather than four call sites in [WatchlistPanel], because the panel's job is the
 * table and each of these is a self-contained conversation about one thing. They are sheets rather
 * than screens for the reason every sheet in this app is: the list stays visible behind them, so a
 * reader changing columns or flags can see what they are changing.
 */
@Composable
internal fun WatchlistSheets(
    sheet: WatchlistSheet?,
    store: WatchlistStore,
    lists: List<Watchlist>,
    activeId: String,
    settings: WatchlistSettings,
    onDismiss: () -> Unit,
    /** Opens this symbol's chart from the row menu. Null on a surface that cannot navigate. */
    onOpenSymbol: ((String) -> Unit)? = null,
    /** Starts a price alert on this symbol. Null where this build has no alert composer. */
    onCreateAlert: ((String) -> Unit)? = null,
) {
    when (sheet) {
        null -> Unit
        WatchlistSheet.Lists -> ListsSheet(store = store, lists = lists, onDismiss = onDismiss)
        WatchlistSheet.Columns -> ColumnsSheet(
            store = store,
            listId = activeId,
            chosen = settings.columns,
            onDismiss = onDismiss,
        )
        WatchlistSheet.Transfer -> TransferSheet(store = store, listId = activeId, onDismiss = onDismiss)
        is WatchlistSheet.RowMenu -> RowSheet(
            store = store,
            lists = lists,
            listId = activeId,
            symbol = sheet.symbol,
            current = settings.flags[sheet.symbol],
            onDismiss = onDismiss,
            onOpenSymbol = onOpenSymbol,
            onCreateAlert = onCreateAlert,
        )
    }
}

/**
 * Making, renaming and deleting lists.
 *
 * The new-list field is at the top rather than behind a plus, because making a second list is the
 * feature this whole sheet exists for and hiding it behind one more tap would be hiding the thing
 * the competition charges for.
 *
 * The default list has no delete control at all — not a greyed one. `WatchlistStore.delete`
 * refuses it either way, but a button that cannot be pressed invites a reader to work out why, and
 * the answer ("your watchlist alerts resolve through it") is not something a disabled button can
 * say. The line under it says it instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListsSheet(store: WatchlistStore, lists: List<Watchlist>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var fresh by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renamed by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Watchlist?>(null) }

    CoineProSheet(
        title = stringResource(R.string.watchlist_lists_title),
        subtitle = stringResource(R.string.watchlist_lists_subtitle),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = CoineProSpacing.Gutter)
                .padding(bottom = CoineProSpacing.Three),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                CoineProTextField(
                    value = fresh,
                    onValueChange = { fresh = it.take(Watchlist.MAX_NAME_LENGTH) },
                    label = stringResource(R.string.watchlist_list_name),
                    modifier = Modifier.weight(1f),
                )
                SheetAction(
                    label = stringResource(R.string.watchlist_create),
                    enabled = fresh.isNotBlank(),
                    onClick = {
                        val name = fresh
                        fresh = ""
                        scope.launch {
                            val id = store.create(name)
                            if (id.isNotEmpty()) store.setActiveList(id)
                        }
                    },
                )
            }

            lists.forEach { list ->
                if (renaming == list.id) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                    ) {
                        CoineProTextField(
                            value = renamed,
                            onValueChange = { renamed = it.take(Watchlist.MAX_NAME_LENGTH) },
                            label = stringResource(R.string.watchlist_list_name),
                            modifier = Modifier.weight(1f),
                        )
                        SheetAction(
                            label = stringResource(R.string.watchlist_save),
                            enabled = renamed.isNotBlank(),
                            onClick = {
                                val name = renamed
                                renaming = null
                                scope.launch { store.rename(list.id, name) }
                            },
                        )
                    }
                } else {
                    // Null rather than a disabled control on the default list: see the sheet note.
                    val onDelete: (() -> Unit)? = if (list.isDefault) null else { { deleting = list } }
                    ListRow(
                        list = list,
                        onRename = {
                            renamed = list.name
                            renaming = list.id
                        },
                        onDelete = onDelete,
                    )
                }
            }
        }
    }

    deleting?.let { list ->
        CoineProConfirmDialog(
            title = stringResource(R.string.watchlist_delete_title, list.name),
            message = stringResource(
                R.string.watchlist_delete_message,
                list.symbols.size.toPersianDigits(),
            ),
            confirmLabel = stringResource(R.string.watchlist_delete_confirm),
            dismissLabel = stringResource(R.string.watchlist_cancel),
            destructive = true,
            onConfirm = {
                deleting = null
                scope.launch { store.delete(list.id) }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** One list in the manage sheet: its name, how many symbols it holds, and what can be done to it. */
@Composable
private fun ListRow(list: Watchlist, onRename: () -> Unit, onDelete: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(CoineProColors.SurfaceElevated)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = list.name,
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = if (list.isDefault) {
                    stringResource(R.string.watchlist_default_locked)
                } else {
                    stringResource(R.string.watchlist_symbol_count, list.symbols.size.toPersianDigits())
                },
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
        }
        IconAction(
            icon = DesignR.drawable.tv_pencil,
            label = stringResource(R.string.watchlist_rename),
            onClick = onRename,
        )
        if (onDelete != null) {
            IconAction(
                icon = CoineProIcons.Delete,
                label = stringResource(R.string.watchlist_delete),
                onClick = onDelete,
            )
        }
    }
}

/**
 * Choosing what the rows show.
 *
 * The two volume columns say plainly that this build's feed does not carry them. Letting a reader
 * tick a box that can only ever produce a column of dashes would be the app wasting their time and
 * then blaming the market for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnsSheet(
    store: WatchlistStore,
    listId: String,
    chosen: Set<WatchlistColumn>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    CoineProSheet(
        title = stringResource(R.string.watchlist_columns_title),
        subtitle = stringResource(R.string.watchlist_columns_subtitle),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = CoineProSpacing.Gutter)
                .padding(bottom = CoineProSpacing.Three),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            WatchlistColumn.entries.forEach { column ->
                val ticked = column in chosen
                val unavailable = column == WatchlistColumn.VOLUME ||
                    column == WatchlistColumn.QUOTE_VOLUME
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CoineProShapes.small)
                        .clickable {
                            val next = if (ticked) chosen - column else chosen + column
                            // An empty set is refused by the store as well; refusing it here too
                            // means the tick simply does not move, rather than moving and snapping
                            // back a frame later.
                            if (next.isNotEmpty()) scope.launch { store.setColumns(listId, next) }
                        }
                        .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.One),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CoineProShapes.extraSmall)
                            .background(if (ticked) CoineProColors.AccentFill else Color.Transparent)
                            .border(1.dp, CoineProColors.Border, CoineProShapes.extraSmall),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ticked) {
                            Icon(
                                painter = painterResource(CoineProIcons.Success),
                                contentDescription = null,
                                tint = CoineProColors.OnAccent,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = column.persianLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = CoineProColors.TextPrimary,
                        )
                        if (unavailable) {
                            Text(
                                text = stringResource(R.string.watchlist_column_no_data),
                                style = MaterialTheme.typography.labelSmall,
                                color = CoineProColors.Warning,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Moving a list in and out as plain text.
 *
 * A text box rather than a file picker, and that is a decision rather than a shortcut. A document
 * picker needs an activity result contract wired in the app module, and what people actually do
 * between two apps on a phone is copy and paste — the export block is one tap to the clipboard and
 * the import block accepts whatever is on it. Nothing here has to be taught, and nothing needs a
 * permission.
 *
 * What did not import is reported, line by line. A silent import is the failure mode that matters:
 * forty lines pasted, thirty-eight arrived, and the two that did not are found a week later when
 * an alert nobody has fires.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferSheet(store: WatchlistStore, listId: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberCoineProHaptics()
    var exported by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<WatchlistImport?>(null) }

    LaunchedEffect(listId) { exported = store.export(listId) }

    CoineProSheet(
        title = stringResource(R.string.watchlist_transfer),
        subtitle = stringResource(R.string.watchlist_transfer_subtitle),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = CoineProSpacing.Gutter)
                .padding(bottom = CoineProSpacing.Three)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            SheetHeading(stringResource(R.string.watchlist_export_title))
            Text(
                text = exported.trim().ifEmpty { stringResource(R.string.watchlist_export_empty) },
                // The tickers are Latin and belong in a left-to-right run even inside this Persian
                // sheet, or a list read as text would come back in the wrong order.
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                color = CoineProColors.TextSecondary,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 132.dp)
                    .clip(CoineProShapes.small)
                    .background(CoineProColors.Stage)
                    .padding(CoineProSpacing.One),
            )
            SheetAction(
                label = stringResource(R.string.watchlist_export_copy),
                enabled = exported.isNotBlank(),
                onClick = {
                    haptics.commit()
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("watchlist", exported))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.size(CoineProSpacing.One))
            SheetHeading(stringResource(R.string.watchlist_import_title))
            Text(
                text = stringResource(R.string.watchlist_import_hint),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
            CoineProTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = stringResource(R.string.watchlist_import_label),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                SheetAction(
                    label = stringResource(R.string.watchlist_import_paste),
                    enabled = true,
                    onClick = {
                        val clip = context.getSystemService(ClipboardManager::class.java)
                            ?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                        if (!clip.isNullOrBlank()) pasted = clip
                    },
                    modifier = Modifier.weight(1f),
                )
                SheetAction(
                    label = stringResource(R.string.watchlist_import_action),
                    enabled = pasted.isNotBlank(),
                    onClick = {
                        val text = pasted
                        scope.launch {
                            result = store.importInto(listId, text)
                            exported = store.export(listId)
                            pasted = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            result?.let { outcome ->
                Text(
                    text = stringResource(
                        R.string.watchlist_import_added,
                        outcome.symbols.size.toPersianDigits(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.Buy,
                )
                if (outcome.rejected.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.watchlist_import_rejected,
                            outcome.rejected.size.toPersianDigits(),
                        ) + "\n" + outcome.rejected.take(REJECTED_SHOWN).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Warning,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * One row's own menu: the colour on it, and taking it off the list.
 *
 * Reached by holding the row. The flag rail on the row itself is three points wide — it is a thing
 * to read down a column, not a thing to hit with a thumb — so the control that sets it is here,
 * where each of the seven gets a target somebody can actually press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowSheet(
    store: WatchlistStore,
    lists: List<Watchlist>,
    listId: String,
    symbol: String,
    current: WatchlistFlag?,
    onDismiss: () -> Unit,
    onOpenSymbol: ((String) -> Unit)?,
    onCreateAlert: ((String) -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    CoineProSheet(
        title = symbol,
        subtitle = stringResource(R.string.watchlist_row_subtitle),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = CoineProSpacing.Gutter)
                .padding(bottom = CoineProSpacing.Three),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            // **The two things a reader wants from a row, above the colours.**
            //
            // The menu used to be flags and a remove, which is a menu about *the list*. A long
            // press on a market is a question about the market: open it, or tell me when it moves.
            // Both are drawn only where the caller has somewhere to send them — a row action that
            // answers a press with nothing is worse than no row action.
            onOpenSymbol?.let { open ->
                SheetAction(
                    label = stringResource(R.string.watchlist_row_open),
                    enabled = true,
                    onClick = {
                        onDismiss()
                        open(symbol)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            onCreateAlert?.let { alert ->
                SheetAction(
                    label = stringResource(R.string.watchlist_row_alert),
                    enabled = true,
                    onClick = {
                        onDismiss()
                        alert(symbol)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Moving one symbol to another list, which the transfer sheet does for a whole list
            // and nothing did for a row. Drawn only where there is a second list to move it to:
            // on a single-list install the row would be a control with one destination, itself.
            lists.filter { it.id != listId }.forEach { target ->
                SheetAction(
                    label = stringResource(R.string.watchlist_row_move, target.name),
                    enabled = true,
                    onClick = {
                        onDismiss()
                        scope.launch {
                            store.add(target.id, symbol)
                            store.remove(listId, symbol)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (onOpenSymbol != null || onCreateAlert != null || lists.size > 1) {
                Spacer(modifier = Modifier.size(CoineProSpacing.One))
            }
            WatchlistFlag.entries.forEach { flag ->
                FlagChoice(
                    label = flag.persianName,
                    swatch = Color(flag.argb.toULong() shl 32),
                    selected = current == flag,
                    onClick = {
                        onDismiss()
                        scope.launch { store.flag(listId, symbol, flag) }
                    },
                )
            }
            FlagChoice(
                label = stringResource(R.string.watchlist_flag_none),
                swatch = null,
                selected = current == null,
                onClick = {
                    onDismiss()
                    scope.launch { store.flag(listId, symbol, null) }
                },
            )
            Spacer(modifier = Modifier.size(CoineProSpacing.One))
            SheetAction(
                label = stringResource(R.string.watchlist_row_remove),
                enabled = true,
                onClick = {
                    onDismiss()
                    scope.launch { store.remove(listId, symbol) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One colour in the row menu, at a size a thumb can hit. */
@Composable
private fun FlagChoice(label: String, swatch: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(if (selected) CoineProColors.SurfaceElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CoineProPillShape)
                .background(swatch ?: Color.Transparent)
                .border(1.dp, if (swatch == null) CoineProColors.Border else Color.Transparent, CoineProPillShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(CoineProIcons.Success),
                contentDescription = null,
                tint = CoineProColors.Accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** A section title inside a sheet. Small, because the sheet's own title is the headline. */
@Composable
private fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = CoineProColors.TextSecondary,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * A text action in a sheet.
 *
 * Not `CoineProBrandButton`, which wants a logo and is sized for a sign-in screen. A flat fill and
 * a hairline, which is what this design system says an elevated control looks like.
 */
@Composable
private fun SheetAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CoineProShapes.small)
            .background(if (enabled) CoineProColors.SurfaceElevated else CoineProColors.Surface)
            .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.small)
            .clickable(enabled = enabled) {
                haptics.commit()
                onClick()
            }
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) CoineProColors.TextPrimary else CoineProColors.TextDisabled,
        )
    }
}

/**
 * How many refused lines the sheet spells out.
 *
 * Enough to recognise the mistake — a wrong venue prefix looks the same on every line — without
 * turning a sheet into a log. The count above them is the whole number either way.
 */
private const val REJECTED_SHOWN = 5
