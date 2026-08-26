package com.coinepro.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.PageAccent
import com.coinepro.core.designsystem.ProvidePageAccent
import com.coinepro.core.designsystem.pageAccent

/**
 * The design system on one page, for review rather than for shipping.
 *
 * Two things it exists to catch. First, the accent rule: four domains, one button component, and
 * they have to be visibly four colours — a single screen cannot show that, because on any one
 * screen a wrong accent still looks deliberate. Second, the surface ladder: five steps that have to
 * be distinguishable from each other in both themes, which is the property that lets this app drop
 * borders between cards and separate them by gap instead.
 */
@Composable
internal fun DesignKit() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        Section("لهجهٔ هر دامنه")
        AccentRow(PageAccent.ANALYSIS, "تحلیل — بازار، چارت، هوش مصنوعی")
        AccentRow(PageAccent.BRAND, "اجرا — معامله، سفارش، حساب")
        AccentRow(PageAccent.SOCIAL, "اجتماعی — کپی‌تریدینگ")

        Section("طلای اشتراک")
        // Not an accent of its own: in this brand the premium gold and the brand gold are the same
        // metal, so premium is marked by the card treatment and its label rather than by hue.
        CoineProCard(modifier = Modifier.fillMaxWidth(), accent = CoineProColors.Premium) {
            Text("اشتراک", style = MaterialTheme.typography.titleSmall, color = CoineProColors.Premium)
            Text(
                "طلای اشتراک لهجهٔ جدا نیست — در این برند همان طلای اصلی است.",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
        }

        Section("نردبان سطح‌ها")
        SurfaceLadder()

        Section("کارت رنگ‌گرفته")
        CoineProCard(modifier = Modifier.fillMaxWidth(), accent = CoineProColors.Warning) {
            Text("هشدار", style = MaterialTheme.typography.titleSmall, color = CoineProColors.Warning)
            Text(
                "پس‌زمینه ۸٪ به سمت رنگ، حاشیه ۳۴٪ — نه آلفا روی زمینهٔ ناشناخته.",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
        }
        CoineProCard(modifier = Modifier.fillMaxWidth(), accent = CoineProColors.Buy) {
            Text("سود", style = MaterialTheme.typography.titleSmall, color = CoineProColors.Buy)
        }
        CoineProCard(modifier = Modifier.fillMaxWidth(), accent = CoineProColors.Sell) {
            Text("ضرر", style = MaterialTheme.typography.titleSmall, color = CoineProColors.Sell)
        }

        Section("متن")
        TextRow("اصلی", CoineProColors.TextPrimary)
        TextRow("ثانویه", CoineProColors.TextSecondary)
        TextRow("کم‌رنگ", CoineProColors.TextMuted)
        TextRow("غیرفعال", CoineProColors.TextDisabled)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = CoineProColors.TextPrimary,
        modifier = Modifier.padding(top = CoineProSpacing.One),
    )
}

@Composable
private fun AccentRow(accent: PageAccent, label: String) {
    ProvidePageAccent(accent) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                CoineProPrimaryButton(text = "اقدام اصلی", onClick = {}, modifier = Modifier.weight(1f))
                CoineProSecondaryButton(text = "ثانویه", onClick = {})
            }
            CoineProCard(modifier = Modifier.fillMaxWidth(), accent = CoineProColors.pageAccent) {
                Text(
                    "کارت انتخاب‌شده در همین دامنه",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SurfaceLadder() {
    val steps = listOf(
        "صحنه" to CoineProColors.Stage,
        "ترمینال" to CoineProColors.Terminal,
        "سطح" to CoineProColors.Surface,
        "برجسته" to CoineProColors.SurfaceElevated,
        "روکش" to CoineProColors.SurfaceOverlay,
        "هاور" to CoineProColors.SurfaceHover,
        "فشرده" to CoineProColors.SurfacePressed,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        steps.forEach { (name, colour) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colour)
                    .padding(horizontal = CoineProSpacing.OneHalf),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(CoineProSpacing.Half))
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            listOf(
                "نازک" to CoineProColors.BorderSubtle,
                "پیش‌فرض" to CoineProColors.Border,
                "پررنگ" to CoineProColors.BorderStrong,
            ).forEach { (name, colour) ->
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(72.dp, 36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CoineProColors.Surface)
                            .androidxBorder(colour),
                    )
                    Text(name, style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextMuted)
                }
            }
        }
    }
}

private fun Modifier.androidxBorder(colour: Color): Modifier =
    border(1.dp, colour, RoundedCornerShape(6.dp))

@Composable
private fun TextRow(label: String, colour: Color) {
    Text(
        text = "$label — ۱۲٬۴۸۰٫۳۵",
        style = MaterialTheme.typography.bodyMedium,
        color = colour,
    )
}
