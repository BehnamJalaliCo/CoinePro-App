package com.coinepro.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * When the app offers to turn notifications on (5.27.0).
 *
 * The owner's rule, after TradingView's: on opening, once a day, until they are on — and never
 * again for a reader who said so. Android stops showing its own dialog after two refusals, so the
 * offer is the app's own sheet, and its button goes to the system's settings once the dialog can
 * no longer be asked for.
 */
object NotificationOfferPolicy {
    fun shouldOffer(enabled: Boolean, never: Boolean, lastOfferDay: Long?, today: Long): Boolean =
        !enabled && !never && lastOfferDay != today
}

const val NOTIFICATION_OFFER_TAG = "notification-offer"

/**
 * The offer itself: a sample of what arrives — a bitcoin move, drawn as the shade would show it —
 * the four kinds, and one button.
 */
@Composable
fun NotificationOfferSheet(
    onEnable: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLater),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .testTag(NOTIFICATION_OFFER_TAG)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CoineProColors.Surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Two),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(CoineProColors.Border),
            )
            SampleNotification()
            Text(
                text = stringResource(R.string.notification_offer_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CoineProColors.TextPrimary,
            )
            OfferRow(CoineProIcons.TrendUp, R.string.notification_offer_moves)
            OfferRow(CoineProIcons.Bell, R.string.notification_offer_watchlist)
            OfferRow(CoineProIcons.News, R.string.notification_offer_digest)
            OfferRow(CoineProIcons.Calendar, R.string.notification_offer_calendar)
            Text(
                text = stringResource(R.string.notification_offer_limits),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
            Spacer(modifier = Modifier.height(CoineProSpacing.Half))
            CoineProPrimaryButton(
                text = stringResource(R.string.notification_offer_enable),
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth(),
                icon = CoineProIcons.Bell,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.notification_offer_later),
                    style = MaterialTheme.typography.labelLarge,
                    color = CoineProColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onLater)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Text(
                    text = stringResource(R.string.notification_offer_never),
                    style = MaterialTheme.typography.labelLarge,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onNever)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun OfferRow(@DrawableRes icon: Int, text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(CoineProColors.Gold.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = CoineProColors.Gold, modifier = Modifier.size(18.dp))
        }
        Text(text = stringResource(text), style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextPrimary)
    }
}

/** What one of these looks like in the shade: the mark, the move, the line. */
@Composable
private fun SampleNotification() {
    val rise = CoineProColors.Buy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CoineProColors.Stage)
            .border(1.dp, CoineProColors.Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoineProAssetLogo(symbol = "BTCUSDT", size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.notification_offer_sample_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.notification_offer_sample_line),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
        }
        // A line drawn the same way in both directions: a chart reads left to right in Persian too.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Canvas(modifier = Modifier.size(width = 64.dp, height = 32.dp)) {
                val points = listOf(0.62f, 0.7f, 0.58f, 0.64f, 0.5f, 0.42f, 0.46f, 0.3f, 0.22f, 0.12f)
                val step = size.width / (points.size - 1)
                val path = Path().apply {
                    points.forEachIndexed { index, y ->
                        val x = index * step
                        if (index == 0) moveTo(x, y * size.height) else lineTo(x, y * size.height)
                    }
                }
                drawPath(path, rise, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(rise, radius = 3.dp.toPx(), center = Offset(size.width, points.last() * size.height))
            }
        }
    }
}
