package com.coinepro.feature.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AuditEvent
import java.time.Instant

/**
 * What one alert actually did.
 *
 * ### Why this sheet is the reason the module exists
 *
 * Somebody who sets an alert stops watching. That is the whole value of the feature, and it is why
 * they trusted it. When the notification does not arrive they do not learn that they were not told
 * — they learn, hours later, that the move happened without them, and they have no way to tell
 * whether the app failed or whether they set the alert wrong. Every «هشدارها کار نمی‌کند» review in
 * this market is somebody who could not distinguish those two, because nothing anywhere wrote down
 * which one it was. The largest chart app on the internet has a 213-vote request for exactly this
 * with no reply.
 *
 * So the first thing on the sheet is a sentence saying what the sheet is for, and the loudest thing
 * on it is a failed delivery. [AuditEvent.FIRED] is the app deciding; [AuditEvent.DELIVERED] is the
 * notification reaching the system; [AuditEvent.DELIVERY_FAILED] is the honest third answer, and it
 * is drawn in the refusal colour on a tinted card because a reader scrolling past it must not be
 * able to miss it.
 *
 * ### Oldest first
 *
 * A firing and its delivery are one event in two lines, and they only read as a pair downwards:
 * «شرط برقرار شد» and then «اعلان نرسید». Newest-first would put every consequence above its cause.
 * These logs are short — one alert's life, not the app's — so nothing is buried by it.
 *
 * ### The date is Persian and the clock is Latin
 *
 * Both, in the same line, deliberately. The day is prose. The time is a figure the reader checks
 * against the candle on their chart, and so is the price beside it, so both stay Latin exactly as
 * they do everywhere else in this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertAuditSheet(view: AlertAuditView, onDismiss: () -> Unit) {
    CoineProSheet(
        title = stringResource(R.string.alerts_audit_title),
        subtitle = view.sentence,
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.alerts_audit_lead),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Right,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        )

        val entries = view.entries.sortedBy(AlertAuditEntry::at)
        if (entries.isEmpty()) {
            if (!view.loading) {
                CoineProEmptyState(
                    message = stringResource(R.string.alerts_audit_empty),
                    icon = CoineProIcons.Pending,
                    hint = stringResource(R.string.alerts_audit_empty_hint),
                    modifier = Modifier.padding(bottom = CoineProSpacing.Four),
                )
            }
            return@CoineProSheet
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = TIMELINE_HEIGHT),
            contentPadding = PaddingValues(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                bottom = CoineProSpacing.Three,
            ),
        ) {
            itemsIndexed(entries, key = { index, entry -> "$index-${entry.at}-${entry.event.id}" }) { index, entry ->
                AuditRow(entry = entry, last = index == entries.lastIndex)
            }
        }
    }
}

/**
 * One line of the log, with the rail that turns a list into a timeline.
 *
 * The rail is a dot and a hairline, not an illustration: it exists so the eye can follow one alert
 * down the sheet without re-reading the dates. The connector is omitted on the last row, because a
 * line running out of the bottom of a list implies there is more below it.
 */
@Composable
private fun AuditRow(entry: AlertAuditEntry, last: Boolean) {
    val failed = entry.event == AuditEvent.DELIVERY_FAILED
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Column(
            modifier = Modifier.width(RAIL_WIDTH).padding(top = CoineProSpacing.OneHalf),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(if (failed) FAILED_DOT else DOT)
                    .clip(CircleShape)
                    .background(entry.event.ink()),
            )
            if (!last) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .weight(1f)
                        .background(CoineProColors.BorderSubtle),
                )
            }
        }

        // The failed row is the only one that gets a surface of its own. Everything else is text on
        // the sheet, so the one that matters is the one the eye lands on.
        if (failed) {
            CoineProCard(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = CoineProSpacing.Half),
                accent = CoineProColors.Sell,
                contentPadding = PaddingValues(
                    horizontal = CoineProSpacing.OneHalf,
                    vertical = CoineProSpacing.OneHalf,
                ),
            ) {
                AuditBody(entry = entry, failed = true)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = CoineProSpacing.OneHalf),
            ) {
                AuditBody(entry = entry, failed = false)
            }
        }
    }
}

/** The words of one entry: what happened, when, at what price, and whatever was noted. */
@Composable
private fun AuditBody(entry: AlertAuditEntry, failed: Boolean) {
    Text(
        text = AlertVocabulary.auditEvent(entry.event),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (failed) FontWeight.Bold else FontWeight.Normal,
        color = if (failed) CoineProColors.Sell else CoineProColors.TextPrimary,
        textAlign = TextAlign.Right,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = entry.factsLine(),
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        textAlign = TextAlign.Right,
        modifier = Modifier.fillMaxWidth(),
    )
    entry.note?.takeIf(String::isNotBlank)?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) CoineProColors.TextSecondary else CoineProColors.TextMuted,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/**
 * «۵ شهریور · 14:30 · قیمت 68,512.40 · H1» — the state the market was in, as recorded then.
 *
 * The price and the timeframe are the two facts a reader cannot reconstruct afterwards, because the
 * market has moved; they are copied into the entry when it is written and printed here verbatim.
 * Both are dropped rather than dashed where the event had none — a deletion has no price, and an
 * alert that never came from a chart has no timeframe.
 */
@Composable
private fun AlertAuditEntry.factsLine(): String {
    val priceLabel = stringResource(R.string.alerts_audit_price)
    return buildList {
        add(PersianDateTime.moment(Instant.ofEpochMilli(at)))
        price?.takeIf { it.isFinite() }?.let { add(priceLabel + " " + MarketNumberFormatter.priceAuto(it)) }
        timeframe?.takeIf(String::isNotBlank)?.let { add(BidiText.isolateLtr(it)) }
    }.joinToString(" · ")
}

/**
 * The dot's colour, which is the only colour in the timeline.
 *
 * Three answers rather than nine. A failed delivery is the refusal colour, an arrival is the
 * confirmation colour, and everything else is a neutral mark — because a log where every event has
 * its own hue is a legend the reader has to learn before they can read one line of it.
 */
@Composable
private fun AuditEvent.ink(): Color = when (this) {
    AuditEvent.DELIVERY_FAILED -> CoineProColors.Sell
    AuditEvent.DELIVERED -> CoineProColors.Buy
    AuditEvent.FIRED -> CoineProColors.Gold
    else -> CoineProColors.BorderStrong
}

/** The rail's width: enough for the dot and the hairline under it, and no more. */
private val RAIL_WIDTH = 16.dp

private val DOT = 8.dp

/** The failed row's dot, deliberately larger. It is the one the reader is looking for. */
private val FAILED_DOT = 11.dp

/** Bounded for the same reason the editor's form is: a lazy list inside a sheet has no height. */
private val TIMELINE_HEIGHT = 460.dp
