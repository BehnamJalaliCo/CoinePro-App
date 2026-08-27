package com.coinepro.feature.journal

import android.content.Intent
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(controller: JournalController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var symbol by rememberSaveable { mutableStateOf("") }
    var buy by rememberSaveable { mutableStateOf(true) }
    var entry by rememberSaveable { mutableStateOf("") }
    var exit by rememberSaveable { mutableStateOf("") }
    var pnl by rememberSaveable { mutableStateOf("") }
    var emotion by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var lesson by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf(listOf<String>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        item { StatsCard(state.stats, state.entries.size) }

        if (state.tags.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                    state.tags.forEach { (tag, count) ->
                        Chip(
                            label = "$tag · ${count.toPersianDigits()}",
                            selected = tag == state.tagFilter,
                            onClick = { controller.setTagFilter(tag) },
                        )
                    }
                }
            }
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

        if (state.entries.isNotEmpty()) {
            item {
                CoineProSecondaryButton(
                    text = stringResource(R.string.journal_export),
                    onClick = {
                        // Shared rather than written to a file the app then has to manage. The
                        // reader picks where it lands — mail to themselves, a notes app, a drive —
                        // and the app keeps no copy of a document it has no business keeping.
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_TEXT, controller.csv())
                        }
                        context.startActivity(
                            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(state.entries, key = JournalEntryEntity::id) { record ->
            EntryCard(record, onDelete = { controller.delete(record) })
        }
    }
}

@Composable
private fun StatsCard(stats: JournalStats, shown: Int) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Line(stringResource(R.string.journal_stat_entries), shown.toPersianDigits())
            // Said out loud rather than hidden: the reader can see that four of their twelve rows
            // carry no number, which is the only way the percentages below are honest.
            if (stats.graded != shown) {
                Line(stringResource(R.string.journal_stat_graded), stats.graded.toPersianDigits())
            }
            stats.winRate?.let {
                Line(
                    stringResource(R.string.journal_stat_winrate),
                    BidiText.isolateLtr(MarketNumberFormatter.price(it, 1) + "%"),
                    if (it >= 50) CoineProColors.Buy else CoineProColors.Sell,
                )
            }
            Line(
                stringResource(R.string.journal_stat_net),
                MarketNumberFormatter.priceAuto(stats.netPnl),
                if (stats.netPnl >= 0) CoineProColors.Buy else CoineProColors.Sell,
            )
            stats.profitFactor?.let {
                Line(stringResource(R.string.journal_stat_pf), BidiText.isolateLtr(MarketNumberFormatter.price(it, 2)))
            }
            stats.expectancy?.let {
                Line(stringResource(R.string.journal_stat_expectancy), MarketNumberFormatter.priceAuto(it))
            }
        }
    }
}

@Composable
private fun EntryCard(record: JournalEntryEntity, onDelete: () -> Unit) {
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
            Text(
                text = stringResource(R.string.journal_delete),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.Sell,
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Line(label: String, value: String, colour: Color = CoineProColors.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colour)
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    accent: Color = CoineProColors.Accent,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
    )
}

private val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/** Persian numerals folded first: `Char.isDigit` keeps them, so a filter alone lets them through. */
private fun String.number(): Double? = foldDigitsToLatin().trim().toDoubleOrNull()
