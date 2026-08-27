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
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField

/**
 * Saved chart layouts.
 *
 * Setting up a chart the way somebody likes it — the type, the timeframe, four indicators — is
 * eight or ten taps, and it is the same eight or ten taps every time they open a different symbol.
 * A layout is that work, kept.
 *
 * What a layout does *not* carry is drawings, and the reason is worth stating where somebody might
 * add them: a trend line is anchored to one instrument's prices and dates. Applying a layout that
 * carried drawings would paste last week's lines onto whatever chart it was applied to, at prices
 * that mean nothing there. A layout is the apparatus, not the annotations.
 *
 * Applying replaces the whole set rather than merging. A layout that added its indicators to
 * whatever was already on would drift towards every indicator being on at once, which is the state
 * a layout exists to escape.
 */
@Composable
internal fun LayoutSheetBody(
    layouts: List<ChartLayout>,
    /** What the chart looks like right now, offered as the thing to save. */
    current: ChartLayout,
    onApply: (ChartLayout) -> Unit,
    onSave: (String) -> Unit,
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
                            // A count rather than a list of ids: the ids are internal, and a reader
                            // recognises their own layout by its name and its size.
                            text = "${layout.indicatorIds.size.toPersianDigits()} اندیکاتور",
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
                            .clickable { onDelete(layout.name) }
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
            text = "ذخیرهٔ چیدمان فعلی",
            onClick = { onSave(name); name = "" },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank(),
        )
        Text(
            text = "چیدمان، نوع چارت و تایم‌فریم و اندیکاتورهای روشن را نگه می‌دارد. ترسیم‌ها را نه — یک خط روند به قیمت‌های همان نماد چسبیده و روی نماد دیگر معنایی ندارد.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.background(CoineProColors.Stage),
        )
        Text(
            text = "چیدمان فعلی: ${current.indicatorIds.size.toPersianDigits()} اندیکاتور",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}
