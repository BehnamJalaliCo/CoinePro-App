package com.coinepro.app.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.app.R
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.proseDigits

/**
 * «پرو چارت پرمیوم» (5.24.0; «پرو» until 5.26.0): what Pro raises, the three plans, and the button that buys one — Cafe
 * Bazaar on the phone, USDT on the site (5.25.0). Where a build sells nothing, a plain «به‌زودی».
 *
 * Everything is free today, and the page says so first: a reader who opens it learns what Pro will
 * add, not that something they use now is about to be taken away. Nothing here names a signal, an
 * exchange or an AI; the store build sells tools only.
 *
 * @param onBuy starts a purchase of one plan id (`monthly`, `quarterly`, `yearly`), or null while
 *   payment is not open on this build — then every plan shows «به‌زودی».
 * @param activeUntil the end of a running subscription, already formatted, or null.
 * @param prices each plan's price as this build sells it, by plan id — USDT on the site (5.25.0).
 *   A plan missing here shows its toman price, which is what Cafe Bazaar charges.
 * @param note one line under the plans — why the button asks for a sign-in, say — or null.
 */
@Composable
fun ProScreen(
    onBuy: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    activeUntil: String? = null,
    prices: Map<String, String> = emptyMap(),
    note: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        ProHero(
            subtitle = activeUntil?.let { stringResource(R.string.pro_active_until, it) }
                ?: stringResource(if (onBuy != null) R.string.pro_lead_open else R.string.pro_lead),
        )

        // Free against Pro, four rows. Prose counts, so Persian digits.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CARD_RADIUS))
                .background(CoineProColors.SurfaceRaised)
                .border(1.dp, CoineProColors.BorderSubtle, RoundedCornerShape(CARD_RADIUS))
                .padding(CoineProSpacing.Two),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            LimitRow(stringResource(R.string.pro_row_feature), stringResource(R.string.pro_col_free), stringResource(R.string.pro_col_pro), header = true)
            LimitRow(stringResource(R.string.pro_row_charts), 2.proseDigits(), 16.proseDigits())
            LimitRow(stringResource(R.string.pro_row_indicators), 5.proseDigits(), 25.proseDigits())
            LimitRow(stringResource(R.string.pro_row_alerts), 5.proseDigits(), 100.proseDigits())
            LimitRow(stringResource(R.string.pro_row_layouts), 1.proseDigits(), stringResource(R.string.pro_unlimited))
        }

        PLANS.forEach { plan ->
            PlanRow(plan = plan, price = prices[plan.id], onBuy = onBuy?.let { buy -> { buy(plan.id) } })
        }

        note?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
        }

        Text(
            text = stringResource(R.string.pro_footer),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/**
 * The menu's Pro card: the gold the palette keeps for the one commercial thing on a page, the name,
 * one line of what it raises, and «به‌زودی» or the running plan at its end.
 */
@Composable
fun ProCard(onOpen: () -> Unit, status: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = CoineProSpacing.Gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(Brush.linearGradient(listOf(CoineProColors.GoldBright, CoineProColors.Gold, CoineProColors.GoldDeep)))
            .clickable(onClick = onOpen)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf)
            .testTag(PRO_CARD_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.pro_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PRO_INK,
            )
            Text(
                text = stringResource(R.string.pro_card_line),
                style = MaterialTheme.typography.bodySmall,
                color = PRO_INK.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.width(CoineProSpacing.One))
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PRO_INK,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.35f))
                .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
        )
    }
}

@Composable
private fun ProHero(subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(Brush.linearGradient(listOf(CoineProColors.GoldBright, CoineProColors.Gold, CoineProColors.GoldDeep)))
            .padding(CoineProSpacing.Two),
    ) {
        Text(
            text = stringResource(R.string.pro_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = PRO_INK,
        )
        Spacer(Modifier.height(CoineProSpacing.Half))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = PRO_INK)
    }
}

@Composable
private fun LimitRow(label: String, free: String, pro: String, header: Boolean = false) {
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    val ink = if (header) CoineProColors.TextSecondary else CoineProColors.TextPrimary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium, fontWeight = weight, color = ink)
        Text(free, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = weight, color = ink, textAlign = TextAlign.Center)
        Text(
            pro,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (header) ink else CoineProColors.GoldDeep,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlanRow(plan: ProPlan, price: String?, onBuy: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(CoineProColors.SurfaceRaised)
            .border(1.dp, CoineProColors.BorderSubtle, RoundedCornerShape(CARD_RADIUS))
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(plan.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CoineProColors.TextPrimary)
            Text(price ?: stringResource(plan.price), style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
        }
        Text(
            text = stringResource(if (onBuy != null) R.string.pro_buy else R.string.pro_soon),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (onBuy != null) PRO_INK else CoineProColors.TextMuted,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (onBuy != null) CoineProColors.Gold else CoineProColors.Surface)
                .then(if (onBuy != null) Modifier.clickable(onClick = onBuy) else Modifier)
                .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One)
                .testTag("pro-buy-${plan.id}"),
        )
    }
}

/** One plan as the page draws it. The id is the server's and the store's (`pro_<id>`). */
internal data class ProPlan(val id: String, val title: Int, val price: Int)

internal val PLANS = listOf(
    ProPlan("monthly", R.string.pro_plan_monthly, R.string.pro_price_monthly),
    ProPlan("quarterly", R.string.pro_plan_quarterly, R.string.pro_price_quarterly),
    ProPlan("yearly", R.string.pro_plan_yearly, R.string.pro_price_yearly),
)

const val PRO_CARD_TAG = "menu-pro-card"

/** Dark ink on gold, in both themes: the card is gold either way. */
private val PRO_INK = Color(0xFF2A1E05)
private val CARD_RADIUS = 16.dp
