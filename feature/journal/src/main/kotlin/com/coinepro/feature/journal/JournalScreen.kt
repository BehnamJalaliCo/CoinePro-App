package com.coinepro.feature.journal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.database.JournalEntryEntity
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProToggleChip
import com.coinepro.core.journal.Journal
import com.coinepro.core.journal.JournalController
import com.coinepro.core.journal.JournalStats
import java.time.Instant

/**
 * The trading journal.
 *
 * The signals list holds what the service published and the portfolio holds what the broker
 * executed. Neither holds the only record that changes how somebody trades: what they thought at
 * the time, and what they would do differently.
 *
 * Everything on this screen is optional except the symbol. A journal is written in the ninety
 * seconds after a trade closes, and a form that demands four numbers before it will save is a
 * journal that stops being kept in the second week. The statistics simply exclude the rows that
 * have no number, and say how many they were rather than quietly averaging them as zeros.
 *
 * ### Filtering, and the figures above it
 *
 * The filter is held here rather than in the controller — [JournalFilter] says why at length, and
 * the short version is that the statistics have to be computed over exactly what the list is
 * showing. Tapping «ریتست» asks what your retests do; a win rate above that list which had quietly
 * stayed at the journal-wide figure would be answering a different question while looking like an
 * answer to yours. The count under the heading says how many of how many, so the reader can always
 * see that a filter is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(controller: JournalController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val screenshots = rememberJournalScreenshots()

    var symbol by rememberSaveable { mutableStateOf("") }
    var buy by rememberSaveable { mutableStateOf(true) }
    var entry by rememberSaveable { mutableStateOf("") }
    var exit by rememberSaveable { mutableStateOf("") }
    var pnl by rememberSaveable { mutableStateOf("") }
    var emotion by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var lesson by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf(listOf<String>()) }

    // Three saveable primitives rather than one saveable filter object: a `Set` needs a custom
    // saver, and three strings survive process death without one.
    var selectedTags by rememberSaveable { mutableStateOf(listOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var outcome by rememberSaveable { mutableStateOf(JournalOutcome.ANY.name) }
    var shot by rememberSaveable { mutableStateOf(JournalShot.ANY.name) }

    val filter = remember(selectedTags, query, outcome, shot) {
        JournalFilter(
            tags = selectedTags.toSet(),
            query = query,
            outcome = JournalOutcome.valueOf(outcome),
            shot = JournalShot.valueOf(shot),
        )
    }
    // Read once, here, and passed to all three: the list, the figures above it and the export
    // under it. That is the whole of the agreement this screen has to keep — the statistics, the
    // CSV and the screenshot filter are three views of one subset, and computing the subset once
    // is the only way they cannot drift apart.
    val attached = screenshots.attached
    val shown = remember(state.entries, filter, attached) { filter.apply(state.entries, attached) }
    val stats = remember(state.entries, filter, attached) { filter.statsOf(state.entries, attached) }

    // One picker for the whole screen rather than one per row. A launcher created inside a lazy
    // item is disposed when that row scrolls off, and the result then arrives with nowhere to go.
    var awaiting by remember { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        val id = awaiting
        awaiting = null
        if (picked != null && id != null) screenshots.attach(id, picked)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        item { StatsCard(stats, shown.size, state.entries.size, filter.isEverything) }

        item {
            FilterCard(
                allTags = state.tags,
                selected = selectedTags,
                query = query,
                outcome = JournalOutcome.valueOf(outcome),
                onToggleTag = { tag ->
                    selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                },
                onQuery = { query = it },
                onOutcome = { outcome = it.name },
                shot = JournalShot.valueOf(shot),
                onShot = { shot = it.name },
            )
        }

        item {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                    Text(
                        text = stringResource(R.string.journal_new),
                        style = MaterialTheme.typography.titleMedium,
                        color = CoineProColors.TextPrimary,
                    )
                    CoineProTextField(symbol, { symbol = it.uppercase() }, stringResource(R.string.journal_symbol), Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                        Chip(stringResource(R.string.journal_buy), buy, CoineProColors.Buy) { buy = true }
                        Chip(stringResource(R.string.journal_sell), !buy, CoineProColors.Sell) { buy = false }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                        CoineProTextField(entry, { entry = it }, stringResource(R.string.journal_entry), Modifier.weight(1f), keyboardOptions = decimal)
                        CoineProTextField(exit, { exit = it }, stringResource(R.string.journal_exit), Modifier.weight(1f), keyboardOptions = decimal)
                    }
                    CoineProTextField(pnl, { pnl = it }, stringResource(R.string.journal_pnl), Modifier.fillMaxWidth(), keyboardOptions = decimal)

                    Text(stringResource(R.string.journal_emotion), style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                        Journal.EMOTIONS.forEach { option ->
                            Chip(option, option == emotion) { emotion = if (emotion == option) "" else option }
                        }
                    }

                    Text(stringResource(R.string.journal_tags), style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                        Journal.SUGGESTED_TAGS.forEach { tag ->
                            Chip(tag, tag in tags) {
                                tags = if (tag in tags) tags - tag else tags + tag
                            }
                        }
                    }

                    CoineProTextField(note, { note = it }, stringResource(R.string.journal_note), Modifier.fillMaxWidth())
                    CoineProTextField(lesson, { lesson = it }, stringResource(R.string.journal_lesson), Modifier.fillMaxWidth())
                    // Said in the form, because the slot itself is on the saved row and a reader
                    // who expected to attach a picture here would otherwise conclude there is none.
                    Text(
                        text = stringResource(R.string.journal_shot_after_saving),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )

                    CoineProPrimaryButton(
                        text = stringResource(R.string.journal_save),
                        onClick = {
                            controller.add(
                                symbol = symbol,
                                buy = buy,
                                entry = entry.number(),
                                exit = exit.number(),
                                size = null,
                                pnl = pnl.number(),
                                emotion = emotion,
                                note = note,
                                lesson = lesson,
                                tags = tags,
                            )
                            symbol = ""; entry = ""; exit = ""; pnl = ""
                            note = ""; lesson = ""; emotion = ""; tags = emptyList()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        // The symbol alone. Everything else is optional on purpose.
                        enabled = symbol.isNotBlank(),
                    )
                }
            }
        }

        if (shown.isNotEmpty()) {
            item {
                CoineProSecondaryButton(
                    // Whatever the filter is showing, not the whole journal. Exporting a different
                    // set from the one on screen is the sort of surprise that is only discovered
                    // in a spreadsheet an hour later.
                    text = stringResource(R.string.journal_export),
                    onClick = {
                        // Shared rather than written to a file the app then has to manage. The
                        // reader picks where it lands — mail to themselves, a notes app, a drive —
                        // and the app keeps no copy of a document it has no business keeping.
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            // The same list and the same screenshot set the figures were computed
                            // from. See JournalExport: the file has to be reconcilable with the
                            // screen it came from, including which entries carry a chart.
                            putExtra(Intent.EXTRA_TEXT, JournalExport.csv(shown, attached))
                        }
                        context.startActivity(
                            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when {
            state.entries.isEmpty() -> item {
                CoineProEmptyState(
                    message = stringResource(R.string.journal_empty),
                    hint = stringResource(R.string.journal_empty_hint),
                )
            }
            shown.isEmpty() -> item {
                CoineProEmptyState(
                    message = stringResource(R.string.journal_no_match),
                    hint = stringResource(R.string.journal_no_match_hint),
                    action = stringResource(R.string.journal_clear_filter),
                    onAction = {
                        selectedTags = emptyList()
                        query = ""
                        outcome = JournalOutcome.ANY.name
                        shot = JournalShot.ANY.name
                    },
                )
            }
        }

        items(shown, key = JournalEntryEntity::id) { record ->
            EntryCard(
                record = record,
                shot = screenshots.uriFor(record.id),
                onAttach = {
                    awaiting = record.id
                    // A document rather than a gallery pick: only `OpenDocument` returns a URI the
                    // app can hold on to across a reboot, which is what a journal needs.
                    picker.launch(arrayOf("image/*"))
                },
                onOpenShot = { uri -> openImage(context, uri) },
                onRemoveShot = { screenshots.detach(record.id) },
                onDelete = {
                    // The picture goes with the row. Left behind it would be a persisted read grant
                    // on an image nothing can ever show again.
                    screenshots.detach(record.id)
                    controller.delete(record)
                },
            )
        }
    }
}

/**
 * The figures, over whatever is on screen.
 *
 * The "shown of total" line is what makes the rest of the card readable. Without it a reader who
 * filtered three screens ago sees a win rate they will quote at themselves and has no way to know
 * it was over eleven of their sixty trades.
 */
@Composable
private fun StatsCard(stats: JournalStats, shown: Int, total: Int, everything: Boolean) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Line(
                label = stringResource(R.string.journal_stat_entries),
                value = if (everything) {
                    shown.toPersianDigits()
                } else {
                    stringResource(R.string.journal_stat_shown, shown.toPersianDigits(), total.toPersianDigits())
                },
            )
            // Said out loud rather than hidden: the reader can see that four of their twelve rows
            // carry no number, which is the only way the percentages below are honest.
            if (stats.graded != shown) {
                Line(stringResource(R.string.journal_stat_graded), stats.graded.toPersianDigits())
            }
            stats.winRate?.let {
                Line(
                    stringResource(R.string.journal_stat_winrate),
                    BidiText.isolateLtr(BidiText.strip(MarketNumberFormatter.price(it, 1)) + "%"),
                    if (it >= 50) CoineProColors.Buy else CoineProColors.Sell,
                )
            }
            Line(
                stringResource(R.string.journal_stat_net),
                MarketNumberFormatter.priceAuto(stats.netPnl),
                if (stats.netPnl >= 0) CoineProColors.Buy else CoineProColors.Sell,
            )
            stats.profitFactor?.let {
                Line(stringResource(R.string.journal_stat_pf), MarketNumberFormatter.price(it, 2))
            }
            stats.expectancy?.let {
                Line(stringResource(R.string.journal_stat_expectancy), MarketNumberFormatter.priceAuto(it))
            }
            stats.averageWin?.let {
                Line(stringResource(R.string.journal_stat_average_win), MarketNumberFormatter.priceAuto(it), CoineProColors.Buy)
            }
            stats.averageLoss?.let {
                Line(stringResource(R.string.journal_stat_average_loss), MarketNumberFormatter.priceAuto(it), CoineProColors.Sell)
            }
        }
    }
}

/**
 * The filter: an outcome, a set of tags, and a text box.
 *
 * The tag cloud is over the whole journal rather than over the filtered subset. A cloud that hid
 * every tag not present in the current selection would leave a reader who filtered to one setup
 * with no visible way back except the chip they pressed, and no way to swap it for another.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterCard(
    allTags: List<Pair<String, Int>>,
    selected: List<String>,
    query: String,
    outcome: JournalOutcome,
    onToggleTag: (String) -> Unit,
    onQuery: (String) -> Unit,
    onOutcome: (JournalOutcome) -> Unit,
    shot: JournalShot,
    onShot: (JournalShot) -> Unit,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = stringResource(R.string.journal_filter),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                OutcomeChip(stringResource(R.string.journal_filter_all), outcome, JournalOutcome.ANY, onOutcome)
                OutcomeChip(stringResource(R.string.journal_filter_wins), outcome, JournalOutcome.WINS, onOutcome)
                OutcomeChip(stringResource(R.string.journal_filter_losses), outcome, JournalOutcome.LOSSES, onOutcome)
                OutcomeChip(stringResource(R.string.journal_filter_ungraded), outcome, JournalOutcome.UNGRADED, onOutcome)
            }
            // A second row rather than more chips in the first: «برد» and «با تصویر» narrow
            // different things, and a reader scanning one strip would read them as alternatives.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                ShotChip(stringResource(R.string.journal_filter_with_shot), shot, JournalShot.WITH, onShot)
                ShotChip(stringResource(R.string.journal_filter_without_shot), shot, JournalShot.WITHOUT, onShot)
            }
            if (allTags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                    allTags.forEach { (tag, count) ->
                        Chip(
                            // The count is a prose count, so Persian digits. The figures in the
                            // card above are market numbers and stay Latin.
                            label = "$tag · ${count.toPersianDigits()}",
                            selected = tag in selected,
                            onClick = { onToggleTag(tag) },
                        )
                    }
                }
            }
            CoineProTextField(
                value = query,
                onValueChange = onQuery,
                label = stringResource(R.string.journal_search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OutcomeChip(
    label: String,
    current: JournalOutcome,
    value: JournalOutcome,
    onSelect: (JournalOutcome) -> Unit,
) {
    Chip(
        label = label,
        selected = current == value,
        accent = when (value) {
            JournalOutcome.WINS -> CoineProColors.Buy
            JournalOutcome.LOSSES -> CoineProColors.Sell
            else -> CoineProColors.Accent
        },
        // Pressing the selected one again goes back to everything, which is the same gesture the
        // tag chips use. One rule for every chip on the screen.
        onClick = { onSelect(if (current == value) JournalOutcome.ANY else value) },
    )
}

/**
 * The screenshot chips.
 *
 * Two rather than three: «همه» is the state you get by pressing the selected one again, which is
 * the rule every other chip on this screen already follows, and a third chip for "no filter" would
 * be a control whose only job is to undo a control beside it.
 */
@Composable
private fun ShotChip(
    label: String,
    current: JournalShot,
    value: JournalShot,
    onSelect: (JournalShot) -> Unit,
) {
    Chip(
        label = label,
        selected = current == value,
        onClick = { onSelect(if (current == value) JournalShot.ANY else value) },
    )
}

@Composable
private fun EntryCard(
    record: JournalEntryEntity,
    shot: Uri?,
    onAttach: () -> Unit,
    onOpenShot: (Uri) -> Unit,
    onRemoveShot: () -> Unit,
    onDelete: () -> Unit,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = BidiText.isolateLtr(record.symbol),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                record.pnl?.let {
                    Text(
                        text = MarketNumberFormatter.priceAuto(it),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (it >= 0) CoineProColors.Buy else CoineProColors.Sell,
                        textAlign = TextAlign.Right,
                    )
                }
            }
            Text(
                // Side and date on one line. A trading diary without a date is a list of
                // observations that cannot be put in order, which is most of what a diary is for.
                text = stringResource(if (record.buy) R.string.journal_buy else R.string.journal_sell) +
                    " · " +
                    PersianDateTime.moment(Instant.ofEpochMilli(record.createdAtEpochMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
            record.note.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
            }
            record.lesson.takeIf(String::isNotBlank)?.let {
                Text(
                    text = stringResource(R.string.journal_lesson) + ": " + it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.Accent,
                )
            }
            record.emotion.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
            }
            if (shot != null) {
                JournalScreenshot(
                    uri = shot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CoineProShapes.small)
                        .clickable { onOpenShot(shot) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                Action(
                    text = stringResource(
                        if (shot == null) R.string.journal_shot_add else R.string.journal_shot_replace,
                    ),
                    tint = CoineProColors.TextSecondary,
                    onClick = onAttach,
                )
                if (shot != null) {
                    Action(
                        text = stringResource(R.string.journal_shot_remove),
                        tint = CoineProColors.TextMuted,
                        onClick = onRemoveShot,
                    )
                }
                Action(
                    text = stringResource(R.string.journal_delete),
                    tint = CoineProColors.Sell,
                    onClick = onDelete,
                )
            }
        }
    }
}

/**
 * Hand the picture to whatever the reader normally views images with.
 *
 * The grant is passed along with the intent, because the viewer has no access of its own to a
 * document this app was given. If nothing on the device can show it the tap simply does nothing —
 * a crash at the end of "look at my screenshot" would be a worse answer than no answer.
 */
private fun openImage(context: Context, uri: Uri) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(view) }
}

@Composable
private fun Action(text: String, tint: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = Modifier
            .clip(CoineProShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
    )
}

@Composable
private fun Line(label: String, value: String, colour: Color = CoineProColors.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colour, textAlign = TextAlign.Right)
    }
}

/**
 * The design system's chip, under this screen's old name.
 *
 * It used to be its own Text with four points of vertical padding — about twenty-three drawn, no
 * press state, no haptic, and an unselected state with no border at all, so a chip nobody had
 * chosen was indistinguishable from a caption. It also filled with `CoineProColors.Accent`, the
 * *ink* gold, and lettered that in `OnAccent`: near-black on dark brown in the light theme.
 *
 * All four are fixed in [CoineProToggleChip] and were fixed there before this screen was written.
 * The only thing worth keeping was the accent parameter, and only for buy/sell, where the colour
 * is the content of the choice rather than a selection state — so it is passed through as a fill.
 */
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    CoineProToggleChip(label = label, selected = selected, onClick = onClick, fill = accent)
}

private val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/** Persian numerals folded first: `Char.isDigit` keeps them, so a filter alone lets them through. */
private fun String.number(): Double? = foldDigitsToLatin().trim().toDoubleOrNull()
