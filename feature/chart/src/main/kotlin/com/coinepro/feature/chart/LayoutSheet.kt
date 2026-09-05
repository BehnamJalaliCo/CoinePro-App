package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.of

/**
 * Saved chart layouts.
 *
 * Setting up a chart the way somebody likes it — the type, the interval, four indicators and the
 * lookbacks under them — is eight or ten taps, and it is the same eight or ten taps every time they
 * open a different symbol. A layout is that work, kept.
 *
 * What a layout does *not* carry is drawings, and the reason is worth stating where somebody might
 * add them: a trend line is anchored to one instrument's prices and dates. Applying a layout that
 * carried drawings would paste last week's lines onto whatever chart it was applied to, at prices
 * that mean nothing there. A layout is the apparatus, not the annotations.
 *
 * Applying replaces the whole set rather than merging. A layout that added its indicators to
 * whatever was already on would drift towards every indicator being on at once, which is the state
 * a layout exists to escape.
 *
 * ### Identity is the id, and the row says so
 *
 * Deleting takes a [ChartLayout.id], not a name. Two layouts are allowed to share a name — a reader
 * who saves «روزانه» twice has two of them — and a delete keyed on the name would remove whichever
 * one the store happened to find first. That was the old shape and it was quietly destructive.
 */
@Composable
internal fun LayoutSheetBody(
    layouts: List<ChartLayout>,
    /** What the chart looks like right now, described under the field that saves it. */
    current: ChartUiState,
    onApply: (ChartLayout) -> Unit,
    onSave: (String) -> Unit,
    /** Takes the layout's id. See the note on identity above. */
    onDelete: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
        layouts.forEach { layout ->
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CoineProShapes.small)
                            .clickable { onApply(layout) }
                            .padding(vertical = CoineProSpacing.Half),
                    ) {
                        Text(
                            text = layout.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = CoineProColors.TextPrimary,
                        )
                        Text(
                            text = layout.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.TextMuted,
                        )
                    }
                    Text(
                        text = "حذف",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Sell,
                        modifier = Modifier
                            .clip(CoineProShapes.small)
                            .clickable { onDelete(layout.id) }
                            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
                    )
                }
            }
        }

        CoineProTextField(
            value = name,
            onValueChange = { name = it },
            label = "نام چیدمان تازه",
            modifier = Modifier.fillMaxWidth(),
        )
        CoineProPrimaryButton(
            text = "ذخیره‌ی چیدمان فعلی",
            onClick = { onSave(name); name = "" },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank(),
        )
        Text(
            text = "چیدمان، نوع چارت و بازه‌ی زمانی و اندیکاتورهای روشن و دوره‌هایشان و مقیاس محور قیمت را نگه می‌دارد. ترسیم‌ها را نه — یک خط روند به قیمت‌های همان نماد چسبیده و روی نماد دیگر معنایی ندارد.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.background(CoineProColors.Stage),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "چیدمان فعلی: ${current.activeIndicators.size.toPersianDigits()} اندیکاتور ·",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
            // The interval is a market figure, so it stays in Latin digits and is isolated: a wire
            // spelling dropped bare into a right-to-left line reorders around the punctuation.
            LtrDirection {
                Text(
                    text = current.interval.wire,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/**
 * What one saved layout says about itself, in a line.
 *
 * The interval and the indicator count, because those are the two things a reader recognises their
 * own layout by. The ids are not shown: they are internal, and a list of them would be longer than
 * the card and would still not say what the layout is *for*.
 *
 * A layout whose stored interval this build cannot resolve prints only the count rather than the
 * raw string. The string is on disk for a reason — a later build may understand it — but a reader
 * has no use for a spelling their app cannot draw.
 */
private fun ChartLayout.summary(): String {
    val count = "${indicators.size.toPersianDigits()} اندیکاتور"
    val resolved = ChartInterval.of(timeframe) ?: return count
    return BidiText.isolateLtr(resolved.wire) + " · " + count
}
