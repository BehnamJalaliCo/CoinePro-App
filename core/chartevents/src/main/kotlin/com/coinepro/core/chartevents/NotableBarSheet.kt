package com.coinepro.core.chartevents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.coinepro.core.chart.NotableBarReading
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import java.time.Instant
import java.time.ZoneId

/**
 * **«چرا این حرکت؟»** — what the dot under an unusually large bar opens.
 *
 * ### It offers, and does not claim
 *
 * The question in the title is the reader's; the answer this sheet gives is *what was on the
 * calendar and on the wire inside that bar's own window*, which is a different and honest thing. A
 * market moves for reasons no app has, and the tempting design — find the nearest headline and put
 * it under «چرا» — would be manufacturing a cause. So where the window holds nothing, the sheet
 * says exactly that, in one sentence, and stops.
 *
 * ### The two numbers, and which digits each gets
 *
 * The **figure** is how many times the bar's range exceeded its own recent normal — the same
 * arithmetic that placed the dot, `NotableBars.ratioAt`, never re-derived here — and the **move**
 * is open to close. Both are market figures a reader compares against another terminal, so both are
 * Latin digits. The count of events under them is prose and is not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotableBarSheet(
    reading: NotableBarReading,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    CoineProSheet(
        title = stringResource(R.string.chart_notable_title),
        subtitle = PersianDateTime.moment(Instant.ofEpochSecond(reading.fromSeconds), zone),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                    reading.ratio?.let { ratio ->
                        Reading(
                            label = stringResource(R.string.chart_notable_size),
                            value = stringResource(R.string.chart_notable_times, formatPrice(ratio, 1)),
                            colour = CoineProColors.TextPrimary,
                        )
                    }
                    reading.movePercent?.let { move ->
                        Reading(
                            label = stringResource(R.string.chart_notable_move),
                            value = stringResource(
                                if (reading.up) {
                                    R.string.chart_notable_move_up
                                } else {
                                    R.string.chart_notable_move_down
                                },
                                // The size of the move; the direction is in the word beside it, so
                                // a stray minus sign inside Persian prose is not also printed.
                                formatPrice(kotlin.math.abs(move), 2),
                            ),
                            colour = if (reading.up) CoineProColors.Buy else CoineProColors.Sell,
                        )
                    }
                }
            }

            if (!reading.explained) {
                // The honest empty state, and the reason it is a sentence rather than a blank: the
                // reader asked a question and «nothing on the calendar or the wire fell inside this
                // bar» is an answer to it.
                Text(
                    text = stringResource(R.string.chart_notable_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
                )
                return@CoineProSheet
            }

            Text(
                text = stringResource(R.string.chart_notable_within),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SHEET_LIST_MAX_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = CoineProSpacing.Two,
                ),
            ) {
                items(reading.events) { event ->
                    // The same row the event sheet draws, so one release looks the same whichever
                    // mark the reader reached it through. Two renderings of one event is one that
                    // will be improved alone.
                    ChartEventRow(event = event, zone = zone)
                }
            }
        }
    }
}

/** One labelled figure. Latin digits, because both of them are market figures. */
@Composable
private fun Reading(label: String, value: String, colour: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = colour,
        )
    }
}
