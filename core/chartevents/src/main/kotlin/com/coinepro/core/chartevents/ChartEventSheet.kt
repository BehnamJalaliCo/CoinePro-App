package com.coinepro.core.chartevents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.ChartEvent
import com.coinepro.core.chart.importanceColour
import com.coinepro.core.chart.EventMark
import com.coinepro.core.chart.Importance
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import java.time.Instant
import java.time.ZoneId

/**
 * What a tapped mark opens.
 *
 * ### A cluster opens all of it
 *
 * A mark that collapsed ten releases into one glyph must open ten rows, in the order they happened.
 * This is the half of the bucketing decision that makes the other half honest: collapsing is only
 * acceptable because nothing is lost by it, and a sheet that showed "the most important one" would
 * be hiding nine events behind a glyph that claims to stand for them. The list scrolls, capped in
 * height, so ten rows are reachable without the sheet swallowing the chart behind it.
 *
 * ### Times are Latin, days are Persian
 *
 * A release time is checked against a session clock, so it is a market figure and stays in Latin
 * digits; the day beside it is prose and does not. That is the line [PersianDateTime] draws for the
 * whole app, and a sheet that sits on top of a chart is the last place to draw it differently.
 *
 * The opt-in is for the sheet state [CoineProSheet] defaults to: the default expression is
 * evaluated here, at the call site, so the experimental marker lands on this function rather than
 * on the design-system one that declares it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartEventSheet(
    mark: EventMark,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    CoineProSheet(
        title = stringResource(R.string.chart_events_sheet_title),
        subtitle = if (mark.isCluster) {
            // A prose count, so Persian digits — «۱۰ رویداد», never «10 رویداد».
            stringResource(R.string.chart_events_sheet_many, mark.events.size.toPersianDigits())
        } else {
            stringResource(R.string.chart_events_sheet_one)
        },
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SHEET_LIST_MAX_HEIGHT)
                .padding(horizontal = CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = CoineProSpacing.Two,
            ),
        ) {
            items(mark.events) { event ->
                ChartEventRow(event = event, zone = zone)
            }
        }
    }
}

/**
 * One event, as a card.
 *
 * Everything the feed gave and nothing it did not: a release with no figures shows no figure line
 * and an item with no attribution shows no source, rather than an em dash standing in for a fact
 * nobody published.
 */
@Composable
private fun ChartEventRow(event: ChartEvent, zone: ZoneId) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EventPill(event.kind.label, CoineProColors.Accent)
                    EventPill(
                        text = stringResource(R.string.chart_events_importance, event.importance.label),
                        colour = event.importance.colour(),
                    )
                }
                Text(
                    text = PersianDateTime.clock(Instant.ofEpochSecond(event.at), zone),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextMuted,
                )
            }
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            event.detail?.let { body ->
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = event.source.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
                Text(
                    text = PersianDateTime.day(Instant.ofEpochSecond(event.at), zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/**
 * The colour a level of importance is said in.
 *
 * The same three the calendar and the news list already use for impact, so a reader who learned
 * them on those screens does not have to learn them again on the chart. Public because the canvas
 * paints its glyph with exactly this, and a second table would be a second chance for the sheet and
 * the axis to disagree about what colour a rate decision is.
 */
@Composable
/**
 * The glyph colour for an event's importance.
 *
 * Delegates rather than repeating the mapping. The canvas draws these marks on the time axis and
 * this sheet lists the same events; two tables for one thing agree until somebody edits one of
 * them, and then the axis and the sheet disagree about which event mattered.
 */
internal fun Importance.colour(): Color = importanceColour(this)

@Composable
private fun EventPill(text: String, colour: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colour.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colour.copy(alpha = 0.32f)),
    ) {
        Text(
            text = text,
            color = colour,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * How tall the list is allowed to get.
 *
 * A cap rather than the sheet's own height: a bar holding ten events would otherwise push the sheet
 * to full screen and take the chart — the thing the reader is looking at — off the display entirely.
 */
private val SHEET_LIST_MAX_HEIGHT = 420.dp
